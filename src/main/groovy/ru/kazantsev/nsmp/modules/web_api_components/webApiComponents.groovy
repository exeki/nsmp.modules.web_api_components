package ru.kazantsev.nsmp.modules.web_api_components

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper

import groovy.transform.Field

import ru.naumen.core.server.script.api.injection.InjectApi
import ru.naumen.core.server.script.spi.AggregateContainerWrapper
import ru.naumen.core.server.script.spi.IScriptDtObject
import ru.naumen.core.server.script.spi.ScriptDate
import ru.naumen.core.shared.dto.ISDtObject

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import java.lang.reflect.Method

import java.text.DateFormat
import java.text.SimpleDateFormat

import java.time.LocalDate
import java.time.LocalDateTime

import static ru.kazantsev.nsd.sdk.global_variables.ApiPlaceholder.utils

//noinspection GroovyUnusedAssignment
@SuppressWarnings("unused")
@Field String MODULE_NAME = 'webApiComponents'

/** Конcтанты модуля */
@SuppressWarnings("unused")
class Constants {
    /** Паттерн для парсинга даты по умолчанию */
    static final String DEFAULT_PARSER_DATE_FORMAT_PATTERN = "dd.MM.yyyy HH:mm:ss"
    /** Коды атрибутов для сериализация вложенных объектов */
    static final List<String> DEFAULT_DT_OBJECT_ATTRS = ['title', 'UUID']
    /** Кодировка по умолчанию */
    static final String DEFAULT_CHARSET = 'UTF-8'
    /** Запись ошибок по умолчанию */
    static final IExceptionWriter DEFAULT_EXCEPTION_WRITER = {
        HttpServletResponse response, Throwable e ->
            try {
                throw e
            } catch (WebApiException webApiException) {
                webApiException.writeToResponseAsJson(response)
            } catch (Exception exception) {
                String errorMessage = "Unexpected error"
                def e500 = new WebApiException.InternalServerError(errorMessage, exception)
                e500.writeToResponseAsJson(response)
            }
    }
}

/**
 * Утилитарный класс, внедряется в closure обработки запроса в RequestProcessor.
 * Переменные запроса будут заранее внедрены.
 */
@InjectApi
@SuppressWarnings("unused")
class WebApiUtilities {

    RequestProcessor processor
    HttpServletRequest request
    HttpServletResponse response
    ISDtObject user
    Preferences prefs

    protected WebApiUtilities(RequestProcessor processor) {
        this.processor = processor
        this.request = processor.request
        this.response = processor.response
        this.user = processor.user
        this.prefs = processor.prefs
    }

    //Внутренние утилитарные методы:

    /**
     * Получить исключение о отсутствии body
     * @return исключение
     */
    protected static WebApiException.BadRequest getNoBodyException() {
        new WebApiException.BadRequest("Отсутствует тело запроса или не передан параметр 'raw' со значением 'true'.")
    }

    /**
     * Получить WebApiException.BadRequest исключение, причиной которого является отсутствие параметра
     * @param paramName имя параметра
     * @return WebApiException.BadRequest исключение
     */
    protected static WebApiException.BadRequest getNoParamException(String paramName) {
        return new WebApiException.BadRequest("Не указан параметр $paramName")
    }

    /**
     * Получить WebApiException.BadRequest исключение, причиной которого является отсутствие хедера
     * @param paramName имя хедера
     * @return WebApiException.BadRequest исключение
     */
    protected static WebApiException.BadRequest getNoHeaderException(String headerName) {
        return new WebApiException.BadRequest("Не указан хедер $headerName")
    }

    /**
     * Получить WebApiException.BadRequest исключение, причиной которого является исключение при попытке конвертации параметра
     * @param paramName имя параметра, где конвертируется параметр
     * @param value значения параметра
     * @param targetClass целевой класс, в который конвертируется
     * @param e исключение
     * @return WebApiException.BadRequest исключение
     */
    protected static WebApiException.BadRequest getParamClassCastException(String paramName, String value, Class targetClass, Exception e) {
        String message = "Не удалось конвертировать в класс ${targetClass.getSimpleName()} параметр $paramName имеющий значение $value."
        return new WebApiException.BadRequest(message, e)
    }

    /**
     * Получить WebApiException.BadRequest исключение, причиной которого является исключение при попытке конвертации хедера
     * @param headerName имя хедера, где конвертируется параметр
     * @param value значения хедера
     * @param targetClass целевой класс, в который конвертируется
     * @param e исключение
     * @return WebApiException.BadRequest исключение
     */
    protected static WebApiException.BadRequest getHeaderClassCastException(String headerName, String value, Class targetClass, Exception e) {
        String message = "Не удалось конвертировать в класс ${targetClass.getSimpleName()} хедер $headerName имеющий значение $value."
        return new WebApiException.BadRequest(message, e)
    }

    /**
     * Преобразовать dtObject SD в пригодный для сериализация объект
     * @param dtObject объект SD
     * @param attrs добавляемые целевой объект поля. По умолчанию все
     * @return пригодный для сериализация объект
     */
    protected Map<String, Object> dtObjectToMap(ISDtObject dtObject, List<String> attrs = null) {
        if (dtObject == null) return null
        if (attrs == null) attrs = dtObject.keySet().toList()
        return attrs.collectEntries { attr ->
            def attrValue = dtObject[attr]
            switch (true) {
                case (attrValue instanceof IScriptDtObject):
                    return [attr, dtObjectToMap(attrValue as ISDtObject, Constants.DEFAULT_DT_OBJECT_ATTRS)]
                case (attrValue instanceof List):
                    return [attr, attrValue.collect { dtObjectToMap(it as ISDtObject, Constants.DEFAULT_DT_OBJECT_ATTRS) }]
                case (attrValue instanceof ScriptDate):
                    return [attr, prefs.getDateFormat().format(attrValue)]
                case (attrValue instanceof AggregateContainerWrapper):
                    attrValue = attrValue as AggregateContainerWrapper
                    Map<String, Object> map = [
                            'employee': dtObjectToMap(attrValue.employee),
                            'team'    : dtObjectToMap(attrValue.team),
                            'ou'      : dtObjectToMap(attrValue.ou)
                    ]
                    return [attr, map]
                default:
                    return [attr, attrValue]
            }
        }
    }

    /**
     * Конвертировать строковый параметр в указанный тип.
     * Может конвертировать в классы Date, LocalDate, LocalDateTime и в любой тип, имеющий статический метод valueOf().
     * @param value конвертируемое значение
     * @param type целевой тип
     * @return конвертируемое значение
     */
    protected <T> T parseValue(String value, Class<T> type) {
        switch (true) {
            case (value == null || value == "null"):
                return null as T
            case (type == String):
                return value as T
            case (type == Date):
                return prefs.getDateFormat().parse(value) as T
            case (type == LocalDate):
                return LocalDate.parse(value, prefs.getDatePattern()) as T
            case (type == LocalDateTime):
                return LocalDateTime.parse(value, prefs.getDatePattern()) as T
            default:
                Method valueOfMethod = type.getMethod("valueOf", String.class)
                return type.cast(valueOfMethod.invoke(null, value))
        }
    }

    //Методы для получения хедеров:

    /**
     * Получить значение хедера в виде определенного класса.
     * Может конвертировать в классы Date, LocalDate, LocalDateTime и в любой класс, имеющий статический метод valueOf().
     * @param paramName имя хедера
     * @param parseAsType целевой класс, по умолчанию String
     * @return Optional содержащий конвертированное значение хедера
     * @throws WebApiException.InternalServerError если указан parseAsType не из списка допустимых
     * @throws WebApiException.BadRequest если не удалось спарсить хедер
     */
    //без модификатор public не компилируется в SD
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    public <T> Optional<T> getHeader(String headerName, Class<T> parseAsType) throws WebApiException.BadRequest, WebApiException.InternalServerError {
        String strValue = request.getHeader(headerName)
        if (strValue == null || strValue?.trim()?.isEmpty()) return Optional.empty()
        try {
            return Optional.of(parseValue(strValue, parseAsType))
        } catch (NoSuchMethodException e) {
            String message = "В метод parseValue передан тип не имеющий прописанного сценария парсинга и метода valueOf()."
            throw new WebApiException.InternalServerError(message, e)
        } catch (Exception e) {
            throw getHeaderClassCastException(headerName, strValue, parseAsType, e)
        }
    }

    /**
     * Получить значение хедера
     * @param paramName имя хедера
     * @return Optional содержащий значение хедера
     */
    Optional<String> getHeader(String headerName) {
        return getHeader(headerName, String)
    }

    /**
     * Получить значение хедера в виде определенного класса или выкинуть исключение.
     * Может конвертировать в классы Date, LocalDate, LocalDateTime и в любой класс, имеющий статический метод valueOf().
     * @param paramName имя хедера
     * @param parseAsType целевой класс, по умолчанию String
     * @return конвертированное значение хедера
     * @throws WebApiException.InternalServerError если указан parseAsType не из списка допустимых
     * @throws WebApiException.BadRequest если не удалось спарсить хедер или хедер не указан
     */
    //без модификатор public не компилируется в SD
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    public <T> T getHeaderElseThrow(String headerName, Class<T> parseAsType) throws WebApiException.BadRequest, WebApiException.InternalServerError {
        return getHeader(headerName, parseAsType).orElseThrow { getNoHeaderException(headerName) }
    }

    /**
     * Получить значение хедера или выкинуть исключение.
     * @param paramName имя хедера
     * @return значение хедера
     * @throws WebApiException.BadRequest если хедер не указан
     */
    String getHeaderElseThrow(String headerName) throws WebApiException.BadRequest {
        return getHeader(headerName, String).orElseThrow { getNoHeaderException(headerName) }
    }

    /**
     * Получить список значений хедера в виде определенного класса.
     * Может конвертировать в классы Date, LocalDate, LocalDateTime и в любой класс, имеющий статический метод valueOf().
     * @param paramName имя хедера
     * @param parseAsType целевой класс, по умолчанию String
     * @return Optional содержащий конвертированные значения хедера
     * @throws WebApiException.InternalServerError если указан parseAsType не из списка допустимых
     * @throws WebApiException.BadRequest если не удалось спарсить хедер
     */
    //без модификатор public не компилируется в SD
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    public <T> Optional<List<T>> getHeaderList(String headerName, Class<T> parseAsType) throws WebApiException.BadRequest, WebApiException.InternalServerError {
        List<String> strValues = request.getHeaders(headerName)?.toList()
        strValues = strValues?.findAll { it != null && !it.trim().isEmpty() }
        if (strValues == null || strValues?.isEmpty()) return Optional.empty()
        List<T> parsed = strValues.collect {
            try {
                parseValue(it, parseAsType)
            } catch (NoSuchMethodException e) {
                String message = "В метод parseValue передан тип не имеющий прописанного сценария парсинга и метода valueOf()."
                throw new WebApiException.InternalServerError(message, e)
            } catch (Exception e) {
                throw getHeaderClassCastException(headerName, it, parseAsType, e)
            }
        }
        return Optional.of(parsed)
    }

    /**
     * Получить список значений хедера
     * @param paramName имя хедера
     * @return Optional содержащий значения хедера
     */
    Optional<List<String>> getHeaderList(String headerName) {
        return getHeaderList(headerName, String)
    }

    /**
     * Получить список значений хедера в виде определенного класса или выкинуть исключение.
     * Может конвертировать в классы Date, LocalDate, LocalDateTime и в любой класс, имеющий статический метод valueOf().
     * @param paramName имя хедера
     * @param parseAsType целевой класс, по умолчанию String
     * @return конвертированное значение хедера
     * @throws WebApiException.InternalServerError если указан parseAsType не из списка допустимых
     * @throws WebApiException.BadRequest если не удалось спарсить хедер или хедер не указан
     */
    //без модификатор public не компилируется в SD
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    public <T> List<T> getHeaderListElseThrow(String headerName, Class<T> parseAsType) throws WebApiException.BadRequest, WebApiException.InternalServerError {
        return getHeaderList(headerName, parseAsType).orElseThrow { getNoHeaderException(headerName) }
    }

    /**
     * Получить список значений хедера или выкинуть исключение.
     * @param paramName имя хедера
     * @return значение хедера
     * @throws WebApiException.BadRequest если хедер не указан
     */
    List<String> getHeaderListElseThrow(String headerName) throws WebApiException.BadRequest {
        return getHeaderList(headerName, String).orElseThrow { getNoHeaderException(headerName) }
    }

    //Методы для получения параметров:

    /**
     * Получить значение параметра в виде определенного класса.
     * Может конвертировать в классы Date, LocalDate, LocalDateTime и в любой класс, имеющий статический метод valueOf().
     * @param paramName имя параметра
     * @param parseAsType целевой класс, по умолчанию String
     * @return Optional содержащий конвертированное значение параметра
     * @throws WebApiException.InternalServerError если указан parseAsType не из списка допустимых
     * @throws WebApiException.BadRequest если не удалось спарсить параметр
     */
    //без модификатор public не компилируется в SD
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    public <T> Optional<T> getParam(String paramName, Class<T> parseAsType) throws WebApiException.InternalServerError, WebApiException.BadRequest {
        String strValue = request.getParameter(paramName)
        if (strValue == null || strValue?.trim()?.isEmpty()) return Optional.empty()
        try {
            return Optional.ofNullable(parseValue(strValue, parseAsType))
        } catch (NoSuchMethodException e) {
            String message = "В метод parseValue передан тип не имеющий прописанного сценария парсинга и метода valueOf()."
            throw new WebApiException.InternalServerError(message, e)
        } catch (Exception e) {
            throw getParamClassCastException(paramName, strValue, parseAsType, e)
        }
    }

    /**
     * Получить значение параметра
     * @param paramName имя параметра
     * @return Optional содержащий значение параметра
     */
    Optional<String> getParam(String paramName) {
        return getParam(paramName, String)
    }

    /**
     * Получить значение параметра в виде определенного класса или выкинуть исключение.
     * Может конвертировать в классы Date, LocalDate, LocalDateTime и в любой класс, имеющий статический метод valueOf().
     * @param paramName имя параметра
     * @param parseAsType целевой класс, по умолчанию String
     * @return конвертированное значение параметра
     * @throws WebApiException.InternalServerError если указан parseAsType не из списка допустимых
     * @throws WebApiException.BadRequest если не удалось спарсить параметр или параметр не указан
     */
    //без модификатор public не компилируется в SD
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    public <T> T getParamElseThrow(String paramName, Class<T> parseAsType) throws WebApiException.InternalServerError, WebApiException.BadRequest {
        return getParam(paramName, parseAsType).orElseThrow { getNoParamException(paramName) }
    }

    /**
     * Получить значение параметра или выкинуть исключение.
     * @param paramName имя параметра
     * @return значение параметра
     * @throws WebApiException.BadRequest если параметр не указан
     */
    String getParamElseThrow(String paramName) throws WebApiException.BadRequest {
        return getParam(paramName, String).orElseThrow { getNoParamException(paramName) }
    }

    /**
     * Получить список значений параметра в виде определенного класса.
     * Может конвертировать в классы Date, LocalDate, LocalDateTime и в любой класс, имеющий статический метод valueOf().
     * @param paramName имя параметра
     * @param parseAsType целевой класс, по умолчанию String
     * @return Optional содержащий конвертированные значения параметра
     * @throws WebApiException.InternalServerError если указан parseAsType не из списка допустимых
     * @throws WebApiException.BadRequest если не удалось спарсить параметр
     */
    //без модификатор public не компилируется в SD
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    public <T> Optional<List<T>> getParamList(String paramName, Class<T> parseAsType) throws WebApiException.InternalServerError, WebApiException.BadRequest {
        List<String> strValues = request.getParameterValues(paramName)
        strValues = strValues?.findAll { it != null && !it.trim().isEmpty() }
        if (strValues == null || strValues?.isEmpty()) return Optional.empty()
        List<T> parsed = strValues.collect {
            try {
                parseValue(it, parseAsType)
            } catch (NoSuchMethodException e) {
                String message = "В метод parseValue передан тип не имеющий прописанного сценария парсинга и метода valueOf()."
                throw new WebApiException.InternalServerError(message, e)
            } catch (Exception e) {
                throw getParamClassCastException(paramName, it, parseAsType, e)
            }
        }
        return Optional.of(parsed)
    }

    /**
     * Получить список значений параметра
     * @param paramName имя параметра
     * @return Optional содержащий конвертированные значения параметра
     */
    Optional<List<String>> getParamList(String paramName) {
        return getParamList(paramName, String)
    }

    /**
     * Получить список значений параметра в виде определенного класса или выкинуть исключение.
     * Может конвертировать в классы Date, LocalDate, LocalDateTime и в любой класс, имеющий статический метод valueOf().
     * @param paramName имя параметра
     * @param parseAsType целевой класс, по умолчанию String
     * @return конвертированное значение параметра
     * @throws WebApiException.InternalServerError если указан parseAsType не из списка допустимых
     * @throws WebApiException.BadRequest если не удалось спарсить параметр или параметр не указан
     */
    //без модификатор public не компилируется в SD
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    public <T> List<T> getParamListElseThrow(String paramName, Class<T> parseAsType) throws WebApiException.InternalServerError, WebApiException.BadRequest {
        return getParamList(paramName, parseAsType).orElseThrow { getNoParamException(paramName) }
    }

    /**
     * Получить список значений параметра или выкинуть исключение.
     * @param paramName имя параметра
     * @return значение параметра
     * @throws WebApiException.BadRequest если параметр не указан
     */
    List<String> getParamListElseThrow(String paramName) throws WebApiException.BadRequest {
        return getParamList(paramName).orElseThrow { getNoParamException(paramName) }
    }

    //Методы для установки тела ответа:

    /**
     * Записать данные в тело ответа как строку
     * @param body тело ответа
     * @param contentType content type ответа БЕЗ указания кодировки. По умолчанию text/plain
     */
    void setBodyAsString(String body, String contentType = null) {
        response.addHeader('Content-Type', contentType)
        if (contentType == null) contentType = "text/plain"
        contentType += ";charset=${Constants.DEFAULT_CHARSET}"
        byte[] bytes = body?.getBytes(Constants.DEFAULT_CHARSET)
        setBodyAsBytes(bytes, contentType)
    }

    /**
     * Записать данные в тело ответа как JSON
     * @param body тело ответа, которое будет сериализовано и записано
     */
    void setBodyAsJson(Object body) {
        if (body instanceof ISDtObject) body = dtObjectToMap(body)
        byte[] bytes
        if (body instanceof String) bytes = body.getBytes(prefs.getCharset())
        else bytes = prefs.getObjectMapper().writeValueAsString(body).getBytes(prefs.getCharset())
        setBodyAsBytes(bytes, "application/json;charset=${prefs.getCharset()}")
    }

    /**
     * Записать данные в тело ответа как JSON из файла системы
     * @param fileDtObject файл системы
     */
    void setBodyAsBytes(ISDtObject fileDtObject) {
        if (fileDtObject == null) return
        if (fileDtObject.getMetainfo().toString() != 'file') {
            String message = "Для записи файла ответ передан ISDtObject у которого метакласс не file."
            throw new WebApiException.InternalServerError(message)
        }
        byte[] fileBytes = utils.readFileContent(fileDtObject as IScriptDtObject)
        response.addHeader('File-Title', (String) fileDtObject.title)
        response.addHeader('File-Description', (String) fileDtObject.description)
        response.addHeader('File-Author-Title', (String) ((ISDtObject) fileDtObject.author)?.title)
        response.addHeader('File-Author-UUID', (String) ((ISDtObject) fileDtObject.author)?.UUID)
        response.addHeader('File-Source-UUID', (String) fileDtObject.source)
        response.addHeader('File-Creation-Date', prefs.getDateFormat().format((Date) fileDtObject.creationDate))
        setBodyAsBytes(fileBytes, (String) fileDtObject.mimeType)
    }

    /**
     * Записать данные в тело ответа как байты
     * @param bytes байты для записи
     * @param contentType mime type файла
     */
    void setBodyAsBytes(byte[] bytes, String contentType) {
        response.addHeader('Content-Type', contentType)
        if (bytes == null || bytes.size() == 0) return
        OutputStream os = response.getOutputStream()
        os.write(bytes, 0, bytes.length)
        os.close()
    }

    //Методы для получения тела запроса:

    /**
     * Получить body запроса как текст
     * @return текст тела запроса
     */
    Optional<String> getBodyAsString() {
        String text = request.getReader().getText()
        if (text == null || text?.size() == 0) return Optional.empty()
        return Optional.of(text)
    }

    /**
     * Получить body запроса как текст, иначе выкинуть исключение
     * @return текст тела запроса
     * @throws WebApiException.BadRequest если тело запроса отсутствует
     */
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    String getBodyAsStringElseThrow() throws WebApiException.BadRequest {
        return getBodyAsString().orElseThrow({ getNoBodyException() })
    }

    /**
     * Получить тело запроса как объект
     * @param clazz тип объекта, в который нужно десериализовать тело запроса
     * @return десериализованный объект
     */
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    public <T> Optional<T> getBodyAsJson(Class<T> clazz = Object) {
        String text = getBodyAsString().orElse(null)
        if (text == null || text?.size() == 0) return Optional.empty()
        else return Optional.of(prefs.getObjectMapper().readValue(text, clazz))
    }

    /**
     * Получить тело запроса как объект, иначе выкинуть исключение
     * @param clazz тип объекта, в который нужно десериализовать тело запроса
     * @return десериализованный объект
     * @throws WebApiException.BadRequest если тело запроса отсутствует
     */
    @SuppressWarnings('GrUnnecessaryPublicModifier')
    public <T> T getBodyAsJsonElseThrow(Class<T> clazz = Object) throws WebApiException.BadRequest {
        return getBodyAsJson(clazz).orElseThrow({ getNoBodyException() })
    }

    /**
     * Получить тело запроса как набор байтов
     * @return массив байтов
     */
    Optional<byte[]> getBodyAsBinary() {
        byte[] bytes = this.getRequest().getInputStream().getBytes()
        if (bytes == null || bytes?.size() == 0) return Optional.empty()
        else return Optional.of(bytes)
    }

    /**
     * Получить тело запроса как набор байтов, иначе выкинуть исключение
     * @return массив байтов
     * @throws WebApiException.BadRequest если тело запроса отсутствует
     */
    byte[] getBodyAsBinaryElseThrow() throws WebApiException.BadRequest {
        return getBodyAsBinary().orElseThrow({ getNoBodyException() })
    }

    //TODO методы получения мультипарт боди

    //Прочие методы:

    /**
     * Создать файл из тела запроса
     * @param dtObject целевой объект SD
     * @param fileName имя файла
     * @param sourceAttr целевой атрибут
     * @param description описание создаваемого файла
     * @return dtObject созданного файла
     */
    ISDtObject attachBodyAsFile(ISDtObject dtObject, String fileName, String sourceAttr = null, String description = null) {
        byte[] bytes = getBodyAsBinary().orElseThrow({ new WebApiException.InternalServerError("Попытка прикрепить файл из боди с пустым контентом.") })
        return utils.attachFile(dtObject as IScriptDtObject, sourceAttr, fileName, request.getContentType() ?: "unknown", description, bytes)
    }

    /**
     * Создать файл из тела запроса
     * @param UUID целевого объекта SD
     * @param fileName имя файла
     * @param sourceAttr целевой атрибут
     * @param description описание создаваемого файла
     * @return dtObject созданного файла
     */
    ISDtObject attachBodyAsFile(String dtObjectUuid, String fileName, String sourceAttr = null, String description = null) {
        return attachBodyAsFile(utils.get(dtObjectUuid), fileName, sourceAttr, description)
    }

}

/** Класс для настроек */
@SuppressWarnings("unused")
class Preferences {

    protected String datePattern = Constants.DEFAULT_PARSER_DATE_FORMAT_PATTERN
    protected String timeZoneId = TimeZone.getDefault().getID()
    protected ObjectMapper objectMapper
    protected DateFormat dateFormat
    protected String charset = Constants.DEFAULT_CHARSET

    protected List<String> assertUser = []
    protected List<String> assertUserGroup = []
    protected Boolean assertUserIsLicensed = false
    protected Boolean assertSuperuser = false
    protected String assertContentType = null
    protected String assertHttpMethod = null

    protected IExceptionWriter exceptionWriter = null

    /**
     * Создать новый экземпляр
     * @return новый экземпляр
     */
    static create() {
        return new Preferences()
    }

    /**
     * Копирует настройки в новый объект.
     * Помогает, когда имеются шаблонные настройки на уровне модуля, часть
     * которых нужно переопределять на уровне методов.
     * @return скопированный экземпляр настроен
     */
    Preferences copy() {
        Preferences prefs = new Preferences()
        prefs.datePattern = this.datePattern
        prefs.timeZoneId = this.timeZoneId
        prefs.objectMapper = this.objectMapper
        prefs.dateFormat = this.dateFormat
        prefs.charset = this.charset
        prefs.assertUser = this.assertUser
        prefs.assertSuperuser = this.assertSuperuser
        return prefs
    }

    /**
     * Установить записыватель (ужасно звучит, я знаю) ответа об ошибке.
     * Принимает на вход объект, реализующий интерфейс IExceptionWriter.
     * Переданный объект в ходе выполнения метода интерфейса должен полностью обеспечить
     * запись данных, то есть записать тело ответа, хедеры, статус.
     * Может принять Closure, но ее сигнатура должна быть идентична методу
     * IExceptionWriter.whiteToResponse(HttpServletResponse response, Exception e),
     * то есть принимать на вход те же аргументы
     * @param exceptionWriter собсна, записыватель
     * @return текущий ответ
     */
    Preferences setExceptionWriter(IExceptionWriter exceptionWriter){
        this.exceptionWriter = exceptionWriter
        return this
    }

    /**
     * Проверить соответствие отправившего запрос пользователя по списку объектов пользователей SD
     * @param users список объектов пользователей или список логинов (или вперемешку)
     * @return текущий объект
     */
    Preferences assertUser(List users) throws WebApiException.BadRequest {
        List<String> logins = []
        users.each {
            if (it instanceof String) logins.add(it)
            else if (it instanceof ISDtObject) logins.add(it.login as String)
            else throw new WebApiException.InternalServerError("В метод assertUser передан List, к в котором содержатся объекты недопустимого класса.")
        }
        this.assertUser = logins
        return this
    }

    /**
     * Проверить наличие группы пользователей у отправившего запрос пользователя
     * @param userGroupCodes перечень кодов групп пользователей
     * @return текущий объект
     */
    Preferences assertUserGroup(List<String> userGroupCodes) {
        this.assertUserGroup = userGroupCodes
        return this
    }

    /**
     * Проверить наличие группы пользователей у отправившего запрос пользователя
     * @param userGroupCodes код группы пользователей
     * @return текущий объект
     */
    Preferences assertUserGroup(String userGroupCode) {
        return assertUserGroup([userGroupCode])
    }

    /**
     * Проверка что пользователь лицензирован
     * @param bool включение\выключение проверки
     * @return текущий объект
     */
    Preferences assertUserIsLicensed(Boolean bool = true) {
        this.assertUserIsLicensed = bool
        return this
    }

    /**
     * Проверить соответствие отправившего запрос пользователя по логину
     * @param login логин
     * @return текущий объект
     */
    Preferences assertUser(String login) {
        return assertUser([login])
    }

    /**
     * Проверить соответствие отправившего запрос пользователя по объекту пользователя SD
     * @param users объект пользователя
     * @return текущий объект
     */
    Preferences assertUser(ISDtObject user) {
        return assertUser([user])
    }

    /**
     * Проверить что отправивший запрос пользователь - суперпользователь
     * @param bool признак. Если tru - будет выполнена проверка
     * @return текущий объект
     */
    Preferences assertSuperuser(Boolean bool = true) {
        this.assertSuperuser = bool
        return this
    }

    /**
     * Проверяет полученный content type на соответствие требуемому
     * если тип не соответствует, вызов метода process вызове ошибку
     * если в запросе не указан content type - проверка будет пропущена
     * @param contentType строка с кодом content type
     * @return текущий объект
     */
    Preferences assertContentType(String contentType) {
        assertContentType = contentType
        return this
    }

    /**
     * Проверяет метод запроса на соответствие указанному
     * @param method требуемый метод запроса
     * @return текущий объект
     */
    Preferences assertHttpMethod(String method) {
        assertHttpMethod = method
        return this
    }

    /**
     * Получить используемую кодировку
     * @return используемая кодировка
     */
    String getCharset() {
        return charset
    }

    /**
     * Установить кодировку
     * @param charset код кодировки
     * @return текущий объект
     */
    Preferences setCharset(String charset) {
        this.charset = charset
        return this
    }

    /**
     * Получить используемый objectMapper
     * @return используемый objectMapper
     */
    ObjectMapper getObjectMapper() {
        if (objectMapper == null) objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setDateFormat(getDateFormat())
                .setTimeZone(TimeZone.getTimeZone(getTimeZoneId()))
        return objectMapper
    }

    /**
     * Установить objectMapper
     * @param objectMapper объект для установки
     * @return текущий объект
     */
    Preferences setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper
        return this
    }

    /**
     * Получить используемый паттерн даты
     * @return используемый паттерн даты
     */
    String getDatePattern() {
        return datePattern
    }

    /**
     * Установить используемый паттерн даты
     * @param datePattern паттерн даты
     * @return текущий объект
     */
    Preferences setDatePattern(String datePattern) {
        this.datePattern = datePattern
        return this
    }

    /**
     * Получить используемый DateFormat
     * @return используемый DateFormat
     */
    protected DateFormat getDateFormat() {
        if (dateFormat == null) dateFormat = new SimpleDateFormat(getDatePattern())
        return dateFormat
    }

    /**
     * Получить используемый код часового пояса
     * @return установить используемый код часового пояса
     */
    String getTimeZoneId() {
        return timeZoneId
    }

    /**
     * Установить код часового пояса
     * @param timeZone код часового пояса
     * @return текущий объект
     */
    Preferences setTimeZone(String timeZone) {
        this.timeZoneId = timeZone
        return this
    }

}

/** Стандартные классы ошибок при работе скрипта */
@SuppressWarnings("unused")
class WebApiException extends RuntimeException {

    Integer status
    String message

    /**
     * @param code - код ошибки
     * @param message - сообщении описывающее ошибку
     */
    WebApiException(Integer status, String message, Throwable cause) {
        super(message, cause)
        this.status = status
        this.message = message
    }

    /**
     * Получить статус ошибки
     * @return статус ошибки
     */
    Integer getStatus() {
        return this.status
    }

    /**
     * Получить данные для записи в боди ответа
     * @return мапа с данными
     */
    Map<String, Object> getDataForJsonResponse() {
        Map<String, Object> body = [
                'message': message,
                'status' : status
        ] as Map<String, Object>
        if (cause != null) body.put(
                'cause',
                [
                        'class'  : cause.getClass().getName(),
                        'message': cause.message
                ]
        )
        return body
    }

    /**
     * Записать данные в ответ
     * @param response ответ
     */
    void writeToResponseAsJson(HttpServletResponse response) {
        response.setStatus(getStatus())
        response.addHeader('Content-Type', 'application/json')
        byte[] bytes = new ObjectMapper().writeValueAsString(getDataForJsonResponse()).getBytes()
        OutputStream os = response.getOutputStream()
        os.write(bytes, 0, bytes.length)
        os.close()
    }

    /** Класс для ошибки используемый при ошибке внутри сервера */
    static class InternalServerError extends WebApiException {
        InternalServerError(String message, Throwable cause = null) {
            super(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, cause)
        }
    }

    /** Класс для ошибки используемый когда пришедшие данные не корректны */
    static class BadRequest extends WebApiException {
        BadRequest(String message, Throwable cause = null) {
            super(HttpServletResponse.SC_BAD_REQUEST, message, cause)
        }
    }

    /** Класс для ошибки используемый когда пользователь не авторизован */
    static class Unauthorized extends WebApiException {
        Unauthorized(String message, Throwable cause = null) {
            super(HttpServletResponse.SC_UNAUTHORIZED, message, cause)
        }
    }

    /** Класс для ошибки используемый когда у пользователя нет прав на операцию */
    static class Forbidden extends WebApiException {
        Forbidden(String message, Throwable cause = null) {
            super(HttpServletResponse.SC_FORBIDDEN, message, cause)
        }
    }

    /** Класс для ошибки используемый когда у пользователя нет прав на операцию */
    static class MethodNotAllowed extends WebApiException {
        MethodNotAllowed(String message, Throwable cause = null) {
            super(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message, cause)
        }
    }

    /** Класс для ошибки используемый когда у пользователя нет прав на операцию */
    static class NotFound extends WebApiException {
        NotFound(String message, Throwable cause = null) {
            super(HttpServletResponse.SC_NOT_FOUND, message, cause)
        }
    }

}

/** Обработчик запросов */
@SuppressWarnings("unused")
class RequestProcessor {

    protected HttpServletResponse response
    protected HttpServletRequest request
    protected ISDtObject user
    protected Preferences prefs
    protected WebApiException preProcessException

    /**
     * Создать новый экземпляр
     * @param request обрабатываемый запрос
     * @param response обрабатываемый ответ
     * @param user обратившийся пользователь
     * @param preferences настройки
     * @return новый экземпляр
     */
    RequestProcessor(HttpServletRequest request, HttpServletResponse response, ISDtObject user, Preferences prefs = null) {
        this.request = request
        this.response = response
        this.user = user
        if (prefs != null) this.prefs = prefs
        else this.prefs = new Preferences()
    }

    /**
     * Создать новый экземпляр
     * @param request обрабатываемый запрос
     * @param response обрабатываемый ответ
     * @param user обратившийся пользователь
     * @param preferences настройки
     * @return новый экземпляр
     */
    static RequestProcessor create(HttpServletRequest request, HttpServletResponse response, ISDtObject user, Preferences preferences = null) {
        return new RequestProcessor(request, response, user, preferences)
    }

    protected assertUserIsLicensed() {
        if (user != null && !((String) user.license).contains('named') && !((String) user.license).contains('concurrent')) {
            throw new WebApiException.Forbidden("Эндпойнт разрешен только для лицензированных пользователей.")
        }
    }

    protected assertUserGroup(List<String> assertUserGroup) {
        if (user != null && user.all_Group.collect { it.code as String }.intersect(assertUserGroup).size() == 0) {
            throw new WebApiException.Forbidden("Недостаточно прав.")
        }
    }

    protected void assertSuperuser() {
        if (user != null) {
            throw new WebApiException.Forbidden("Эндпойнт разрешен только для суперпользователя.")
        }
    }

    protected void assertUser(List<String> assertUsers) {
        if (user != null && (String) user?.login !in assertUsers) {
            throw new WebApiException.Forbidden("Эндпойнт не разрешен для пользователя ${user?.login}.")
        }
    }

    protected void assertHttpMethod(String method) {
        List<String> METHOD_ARR = ['GET', 'POST']
        String currentMethod = this.request.getMethod()
        if (method.toUpperCase() !in METHOD_ARR) {
            throw new WebApiException.InternalServerError("В метод assertHttpMethod() " +
                    "передан неизвестный HTTP метод. Допустимые значения: ${METHOD_ARR.join(', ')}")
        }
        if (method.toLowerCase() != currentMethod.toLowerCase()) {
            throw new WebApiException.MethodNotAllowed("HTTP " +
                    "метод ${currentMethod} не разрешен для данного эндпойнта.")
        }
    }

    protected void assertContentType(String contentType) {
        String getContentType = request.getContentType()
        if (getContentType && !getContentType.contains(contentType)) {
            throw new WebApiException.BadRequest("Требуемый content type - " +
                    "\"${contentType}\", полученный - \"${getContentType}\".")
        }
    }

    protected void preProcessAssert() {
        //Проверка что обращается суперпользователь
        if (prefs.assertSuperuser) assertSuperuser()
        //Проверка что пользователь лицензирован
        if (prefs.assertUserIsLicensed) assertUserIsLicensed()
        //Проверка что пользователь имеет определенную группу пользователей
        if (!prefs.assertUserGroup.isEmpty()) assertUserGroup(prefs.assertUserGroup)
        //Проверка что пользователь входит в список разрешенных
        if (!prefs.assertUser.isEmpty()) assertUser(prefs.assertUser)
        //Проверка на HTTP метод
        if (prefs.assertHttpMethod != null) assertHttpMethod(prefs.assertHttpMethod)
        //Проверка на Content-Type
        if (prefs.assertContentType != null) assertContentType(prefs.assertContentType)
    }

    /**
     * Запуск процесса обработки запроса
     * @param action действие для обработки запроса
     */
    void process(Closure action) {
        try {
            preProcessAssert()
            WebApiUtilities webApiUtilities = new WebApiUtilities(this)
            action(webApiUtilities)
        } catch (Exception e1) {
            if (prefs.exceptionWriter != null) {
                try {
                    prefs.exceptionWriter.whiteToResponse(response, e1)
                } catch (MissingMethodException e2) {
                    String message
                    if (e2 !instanceof MissingMethodException) message = "Переданный в настройки exceptionWriter не смог записать ошибку при обработке исключения"
                    else message = "Переданный в настройки exceptionWriter не смог записать ошибку при обработке исключения, тк не реализует интерфейс IExceptionWriter"
                    new WebApiException.InternalServerError(message, e2).writeToResponseAsJson(response)
                }
            } else Constants.DEFAULT_EXCEPTION_WRITER.whiteToResponse(response, e1)
        }
    }
}

/**
 * Интерфейс, объект которого реализуют запись данных об ошибке в ответ
 * Устанавливается в Preferences
 */
interface IExceptionWriter {
    void whiteToResponse(HttpServletResponse response, Exception e)
}
