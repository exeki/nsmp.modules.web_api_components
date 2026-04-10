# WebApiComponents

Если вам не нужны размусоливания почему так делать правильно и зачем это все нужно, то листайте до раздела "Описание инструмента".
## Что это и зачем это нужно:

Библиотека предназначена для упрощения построения контроллеров в NSD при использовании внедряемых в функции переменных request, response, user. Модуль работает только с этими переменными, тк через них можно получить максимальное количество информации о запросе. Подробнее о них можно прочитать в [документации](https://www.naumen.ru/docs/sd/nsmp/4.17.5/Content/RESTful/REST_API_method.htm), раздел **"Получение дополнительной информации о запросе в параметрах метода (params)"**.

Основным преимуществом такого подхода является то, что при модификации метода его сигнатура останется неизменной, тк все данные запроса получаются из аргумента request.

**Пример:**

Допустим в модуле ru.kazantsev.nsmp.modules.web_api_components.exampleModule объявлена функция:

```groovy
String badExample(String str1) {
    return str1 + " доп текст"
}
```

Url для запроса будет выглядеть так:

```
https://my-sd.ru/sd/services/rest/exec?func=modules.ru.kazantsev.nsmp.modules.web_api_components.exampleModule.badExample&params=someStr&accessKey=myAccessKey
```

Ответ будет следующий:

```groovy
someStr доп текст
```

И в целом все будет выглядеть и работать замечательно. Но функционал имеет свойство расширяться, а значит и функция badExample может быть модифицирована, допустим она станет такой:

```groovy
String badExample(String str1, String str2) {
    return str1 + " доп текст " + str2
}
```

И в таком случае если пользователь не был проинформирован о том что функция изменена и выполнил запрос по тому же url, что и выше, он столкнется с ответом:

```
Script module or method not found: ru.kazantsev.nsmp.modules.web_api_components.exampleModule.badExample(1 args)
```

Взглянем на пример ниже:

```groovy
class ExampleWebException extends Exception {
    int status

    ExampleWebException(int status, String message) {
        super(message)
        this.status = status
    }
}

void badExample(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    try {
        String str1 = request.getParameter('str1')
        if (str1 == null) throw new ExampleWebException(400, "не указан параметр str1")
        String str2 = request.getParameter('str2')
        if (str2 == null) throw new ExampleWebException(400, "не указан параметр str2")
        response.writer.write(str1 + " доп текст " + str2)
        response.writer.close()
    } catch (ExampleWebException e) {
        response.setStatus(e.status)
        response.writer.write(e.message)
        response.writer.close()
    } catch (Exception e) {
        response.setStatus(500)
        response.writer.write("Непредвиденная ошибка: " + e.message)
        response.writer.close()
        throw e
    }
}
```

Для запроса пользователь будет использовать такой URL:

```
https://my-sd.ru/sd/services/rest/exec?func=modules.ru.kazantsev.nsmp.modules.web_api_components.exampleModule.badExample&params=request,response,user&accessKey=myAccessKey&str1=someValue&str2=someValue
```

В данном случае ситуация выглядит лучше (но далеко не идеально). Функция имеет возможность получать параметр из нативныхдля протокола HTTP источников и соответствующе реагировать на их отсутствие или наличие, отдавая пользователю правильный статус ответа, перехватывая объявленное исключение ExampleWebException.
Это улучшение функции стоило нескольких строк кода, и каждый параметр будет растягивать функцию еще больше.

Данный модуль призван упросить решение перечисленных выше проблем:

1. Риск смены сигнатуры метода при его доработке;
2. Отсутствие внятного обработчика ошибок;
3. Большое количество кода при получении и обработке элементов запроса.

## Описание инструмента

Библиотека предоставляет 4 основные фичи:

1. Контейнер для обработки запросов;
2. Методы для проверки метаданных запроса и пользователя запроса;
3. Утилитарный класс для чтения и записи данных;
4. Набор исключений, которые могут быть использованы для отдачи ошибок пользователю.

### RequestProcessor - контейнер для обработки запросов 

За обработку запросов прежде всего отвечает класс RequestProcessor.

```groovy
import ru.kazantsev.nsd.modules.web_api_components.HttpServletRequest
import ru.kazantsev.nsd.modules.web_api_components.WebApiUtilities

void exampleGet1(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user).process {
        WebApiUtilities webUtils ->
            //some code here
    }
}
```

В примере выше создается экземпляр RequestProcessor при помощи метода create, внедряя в него параметры запроса.
Сам код выполнения методы должен содержаться в Closure пeрeданной в метод process. Важно: возвращенные в Closure данные
будут проигнорированы,
тк есть риск попытки двойной записи данных в ответ (что приведет к выскакиванию ошибки об этом в консоли каждый раз при
запросе).

В Closure будет внедрен автоматически созданный экземпляр WebApiUtilities, который позволит упросить операции по чтению
и записи данных (об этом расскажу чуть позже).
Но наличие утилитарного класса не отменяет возможность использования нативных request, response, user в Closure.

### Preferences - конфигурационный класс

Для конфигурации используется класс Preferences. Он позволяет выполнить различные проверки перед обработкой запроса, а так же сконфигурировать саму обработку.
Класс имеет метод copy(), позволяющий скопировать экземпляр, что бы переопределить или дополнить его настройки на уровне конкретного запроса. 

```groovy
@Field ObjectMapper ob = new ObjectMapper() //допустим тут была выполнена конфигурация ObjectMapper
@Field Preferences prefs = Preferences.create().assertUser("userLogin").setObjectMapper(ob)

void exampleGet1(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertContentType('application/json')).process {
        WebApiUtilities webUtils ->
            //some code here
    }
}
```

Для проверок используются методы:


1. Проверка что пользователь имеет определенную группу пользователей
```groovy
Preferences.assertUserGroup(List<String> userGroupCodes)
```
2. Проверка что пользователь лицензирован
```groovy
Preferences.assertUserIsLicenced(Boolean bool = true)
```
3. Проверка что общается пользователь из списка (метод принимает объект пользователей и логины)
```groovy
Preferences.assertUser(List users)
```
4. Проверка что обращается суперпользователь```groovy
```groovy
Preferences.assertSuperuser(Boolean bool = true)
```
5. Проверка что при обращении указывается конкретный contentType
```groovy
Preferences.assertContentType(String contentType)
```
6. Проверка что обращение происходит по определенному HTTP методы (GET или POST)
```groovy
Preferences.assertHttpMethod(String method) 
```

Для конфигурации используются методы:
```groovy
//Установить кодировку для чтения и отдачи данных
Preferences setCharset(String charset)
//Установить ObjectMapper
Preferences setObjectMapper(ObjectMapper objectMapper)
//Установить паттерн даты
Preferences setDatePattern(String datePattern)
//Установить код часового пояса
Preferences setTimeZone(String timeZone)
//Установить кастомизированный способ записи ошибок. Может принимать на вход closure, 
//сигнатура которой идентична единственному методу интерфейса IExceptionWriter
Preferences setExceptionWriter(IExceptionWriter exceptionWriter)
```

Метод **setExceptionWriter** позволяет вам организовать запись ответа самостоятельно, например:

```groovy
void exampleWithCustomExceptionWriter1(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    //Устанавливаем в роли обработчика запроса Closure, которая реализует интерфейс IExceptionWriter
    Preferences prefs = new Preferences().setExceptionWriter { HttpServletResponse resp, Exception exc ->
        WebApiException webExc = exc instanceof WebApiException ? exc : new WebApiException.InternalServerError("Unexpected error", exc)
        Map errorData = ['result': null, 'error': webExc.getDataForJsonResponse()]
        resp.addHeader('Content-Type', 'application/json')
        resp.setStatus(webExc.getStatus())
        byte[] bytes = new ObjectMapper().writeValueAsString(errorData).getBytes()
        OutputStream os = resp.getOutputStream()
        os.write(bytes, 0, bytes.length)
        os.close()
    }
    RequestProcessor.create(request, response, user, prefs).process {
        WebApiUtilities webUtils ->
            throw new RuntimeException("Это исключение будет записано особым образом")
    }
}
```

Аналогичное поведение будет, если создать отдельный класс реализующий интерфейс IExceptionWriter:

```groovy
class ru.kazantsev.nsmp.modules.web_api_components.CustomExceptionWriter implements IExceptionWriter {
    @Override
    void whiteToResponse(HttpServletResponse resp, Exception exc) {
        WebApiException webExc = exc instanceof WebApiException ? exc : new WebApiException.InternalServerError("Unexpected error", exc)
        Map errorData = ['result': null, 'error': webExc.getDataForJsonResponse()]
        resp.addHeader('Content-Type', 'application/json')
        resp.setStatus(webExc.getStatus())
        byte[] bytes = new ObjectMapper().writeValueAsString(errorData).getBytes()
        OutputStream os = resp.getOutputStream()
        os.write(bytes, 0, bytes.length)
        os.close()
    }
}

void exampleWithCustomExceptionWriter2(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    Preferences prefs = new Preferences().setExceptionWriter(new ru.kazantsev.nsmp.modules.web_api_components.CustomExceptionWriter())
    RequestProcessor.create(request, response, user, prefs).process {
        WebApiUtilities webUtils ->
            throw new RuntimeException("Это исключение будет записано особым образом")
    }
}
```

### WebApiUtilities - утилитарный класс 

Класс WebApiUtilities используется для упрощения работы с поступившими и отдаваемыми данными. Экземпляр класса автоматически создается и внедряется в Closure, 
переданную в процесс, по этому при написании Closure для обработки запроса нужно обязательно обозначить ее сигнатуру как входной аргумент Closure.

Основные фичи утилитарного класса:
1. Набор методов начинающихся с getParam для получения параметров запроса в типизированном и/или обязательно порядке.
2. Набор методов начинающихся с getHeader для получения хедеров запроса в типизированном и/или обязательно порядке.
3. Набор методов начинающихся с getBody для получения тела запроса в типизированном и/или обязательно порядке (встроенный парсер json).
4. Набор методов начинающихся с setBody для установки тела запроса в требуемом виде без лишних телодвижений.

Пример возможностей:
```groovy
void examplePost1(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, new Preferences().setDatePattern("dd.MM.yyyy HH:mm:ss")).process {
        WebApiUtilities webUtils ->
            //Получение необязательных параметров
            String stringParam = webUtils.getParam('stringParamName').orElse(null)
            //Получение параметров с встроенной конвертацией в описанный перечень классов (400 ответ в случае неудачной попытки конвертации)
            Date dateParam = webUtils.getParam('dateParamName', Date).orElse(null)
            //Получение обязательных параметров, пользователю будет отправлен 400 ответ если их не будет в запросе
            Long longParam = webUtils.getParamElseThrow('longParamName', Long)
            Boolean booleanParam = webUtils.getParamElseThrow('booleanParamName', Boolean)
            //Есть возможность работы с массивами параметров
            List<String> listStringParam = webUtils.getParamList("listStringParam").orElse(null)
            List<Long> listLongParam = webUtils.getParamListElseThrow("listLongParam", Long)

            //Аналогичная ситуация с хедерами:
            Boolean aBooleanHeader = webUtils.getHeader("aBooleanHeader", Boolean).orElse(null)
            String stringHeader = webUtils.getHeaderElseThrow("stringHeader")
            List<Long> longListHeader = webUtils.getHeaderList("longListHeader", Long).orElse(null)
            List<Date> dateListHeader = webUtils.getHeaderListElseThrow("dateListHeader", Date)

            //Возможно типизированное и не типизированное получение боди
            ru.kazantsev.nsmp.modules.web_api_components.TestBody2 body = webUtils.getBodyAsJsonElseThrow(ru.kazantsev.nsmp.modules.web_api_components.TestBody2)
            //Для работы с другими типами тела запроса есть методы:
            //webUtils.getBodyAsString()
            //webUtils.getBodyAsBinary()

            //простая установка тела ответа
            Map responseBody = [
                    'message': 'Получены данные',
                    'params' : [
                            'stringParam'    : stringParam,
                            'dateParam'      : dateParam,
                            'longParam'      : longParam,
                            'booleanParam'   : booleanParam,
                            'listStringParam': listStringParam,
                            'listLongParam'  : listLongParam
                    ],
                    'headers': [
                            'aBooleanHeader': aBooleanHeader,
                            'stringHeader'  : stringHeader,
                            'longListHeader': longListHeader,
                            'dateListHeader': dateListHeader
                    ],
                    'body'   : body
            ]
            webUtils.setBodyAsJson(responseBody)
            //доступ к базовым параметрам запроса остается, можно выполнять любые операции по своему
            response.setHeader('message', 'Hello World!')
    }
}
```

### WebApiException - набор исключений, обрабатываемый контейнером запроса 

Класс WebApiException сам по себе является исключением, которое в случае выкидывания в контейнере запросов RequestProcessor 
передаст ответ с установленным при выкидывании исключения статусом.  

WebApiException содержит в себе ряд вложенных классов - конкретизированных исключений, таких как:
1. WebApiException.NotFound
2. WebApiException.MethodNotAllowed
3. WebApiException.Forbidden
4. WebApiException.Unauthorized
5. WebApiException.BadRequest
6. WebApiException.InternalServerError

Они так же обрабатываются контейнером запросов. 
Чаще всего эти исключение выкидываются методами утилитарного класса, которые заканчиваются на ElseThrow (в случае отсутствия запрошенной части запроса)
или при ошибке конвертации данных запроса, а так же при выполнении различных assert в классе Preferences.
Но вы можете использовать эти и исключения самостоятельно, или же создать свои, главное что бы они наследовались от класса WebApiException.

Пример использования:
```groovy
void exampleGet1(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, new Preferences().setDatePattern("dd.MM.yyyy HH:mm:ss")).process {
        WebApiUtilities webUtils ->
            //В случае отсутствия параметра будет выкинуто исключение с заранее прописанным сообщение
            String stringParam1 = webUtils.getParamElseThrow('someParam1')
            //Имеем сами возможность обработать отсутствие параметра, в данном случае выкинем исключение
            String stringParam2 = webUtils.getParam('someParam2').orElseThrow { new WebApiException.BadRequest("Ты забыл указать параметр") }
            Boolean dontLikeYou = true
            if (dontLikeYou) {
                new WebApiException.Forbidden("Ты мне просто не нравишься, ухади")
            }
    }
}
```
