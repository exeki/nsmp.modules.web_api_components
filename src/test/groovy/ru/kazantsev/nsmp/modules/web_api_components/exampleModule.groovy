package ru.kazantsev.nsmp.modules.web_api_components
//file:noinspection GrMethodMayBeStatic
//file:noinspection unused


import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.Field
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import ru.naumen.core.shared.dto.ISDtObject


@Field Preferences prefs = new Preferences().assertSuperuser().setDatePattern('dd.MM.yyyy HH:mm:ss')

//тест что эндпойнт доступ только суперпользователю
void assertSuperUserTest(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy()).process {
        WebApiUtilities webUtils ->
            webUtils.setBodyAsJson(['message': 'Доступно только для суперпользователя'])
    }
}

//тест что эндпойнт доступен только пользователю с логином eadmintest и суперпользователю
void assertUserTest(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertSuperuser(false).assertUser('eadmintest')).process {
        WebApiUtilities webUtils ->
            webUtils.setBodyAsJson(['message': 'Доступно только для пользователя с логином eadmintest'])
    }
}

//тест что эндпойнт доступен только пользователю с логином eadmintest и суперпользователю
void assertUserGroupTest(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertSuperuser(false).assertUserGroup('C3_admin')).process {
        WebApiUtilities webUtils ->
            webUtils.setBodyAsJson(['message': 'Доступно только для пользователя с правами C3_admin'])
    }
}

//тест что эндпойнт доступен только пользователю с логином eadmintest и суперпользователю
void assertUserIsLicensedTest(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertSuperuser(false).assertUserIsLicensed()).process {
        WebApiUtilities webUtils ->
            webUtils.setBodyAsJson(['message': 'Доступно только для лиц пользователя'])
    }
}

//тест что эндпойнт доступ только по методу GET
void assertHttpMethod(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertHttpMethod('GET')).process {
        WebApiUtilities webUtils ->
            webUtils.setBodyAsJson(['message': 'Доступно только по методу GET'])
    }
}

//тест что указан контент тайп 'application/json'
void assertContentType(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertContentType('application/json')).process {
        WebApiUtilities webUtils ->
            webUtils.setBodyAsJson(['message': 'Доступно только c Content-Type application/json'])
    }
}

//тест получения параметров
void testParameters(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertContentType('application/json')).process {
        WebApiUtilities webUtils ->
            Map<String, Object> body = [
                    'string'    : webUtils.getParam('string').orElse(null),
                    'stringList': webUtils.getParamList('stringList').orElse(null),
                    'double'    : webUtils.getParam('double', Double).orElse(null),
                    'doubleList': webUtils.getParamList('doubleList', Double).orElse(null),
                    'long'      : webUtils.getParam('long', Long).orElse(null),
                    'longList'  : webUtils.getParamList('longList', Long).orElse(null),
                    'date'      : webUtils.getParam('date', Date).orElse(null),
                    'dateList'  : webUtils.getParamList('dateList', Date).orElse(null),
                    'boolean'   : webUtils.getParam('boolean', Boolean).orElse(null)
            ]
            webUtils.setBodyAsJson(body)
    }
}

//тест получения обязательных параметров
void testParametersRequired(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertContentType('application/json')).process {
        WebApiUtilities webUtils ->
            Map<String, Object> body = [
                    'string'    : webUtils.getParamElseThrow('string'),
                    'stringList': webUtils.getParamListElseThrow('stringList'),
                    'double'    : webUtils.getParamElseThrow('double', Double),
                    'doubleList': webUtils.getParamListElseThrow('doubleList', Double),
                    'long'      : webUtils.getParamElseThrow('long', Long),
                    'longList'  : webUtils.getParamListElseThrow('longList', Long),
                    'date'      : webUtils.getParamElseThrow('date', Date),
                    'dateList'  : webUtils.getParamListElseThrow('dateList', Date),
                    'boolean'   : webUtils.getParamElseThrow('boolean', Boolean)
            ]
            webUtils.setBodyAsJson(body)
    }
}

//тест получения хедеров
void testHeaders(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy()).process {
        WebApiUtilities webUtils ->
            Map<String, Object> body = [
                    'string'    : webUtils.getHeader('string').orElse(null),
                    'stringList': webUtils.getHeaderList('stringList').orElse(null),
                    'double'    : webUtils.getHeader('double', Double).orElse(null),
                    'doubleList': webUtils.getHeaderList('doubleList', Double).orElse(null),
                    'long'      : webUtils.getHeader('long', Long).orElse(null),
                    'longList'  : webUtils.getHeaderList('longList', Long).orElse(null),
                    'date'      : webUtils.getHeader('date', Date).orElse(null),
                    'dateList'  : webUtils.getHeaderList('dateList', Date).orElse(null),
                    'boolean'   : webUtils.getHeader('boolean', Boolean).orElse(null)
            ]
            webUtils.setBodyAsJson(body)
    }
}

//тест получения обязательных хедеров
void testHeadersRequired(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy()).process {
        WebApiUtilities webUtils ->
            Map<String, Object> body = [
                    'string'    : webUtils.getHeaderElseThrow('string'),
                    'stringList': webUtils.getHeaderListElseThrow('stringList'),
                    'double'    : webUtils.getHeaderElseThrow('double', Double),
                    'doubleList': webUtils.getHeaderListElseThrow('doubleList', Double),
                    'long'      : webUtils.getHeaderElseThrow('long', Long),
                    'longList'  : webUtils.getHeaderListElseThrow('longList', Long),
                    'date'      : webUtils.getHeaderElseThrow('date', Date),
                    'dateList'  : webUtils.getHeaderListElseThrow('dateList', Date),
                    'boolean'   : webUtils.getHeaderElseThrow('boolean', Boolean)
            ]
            webUtils.setBodyAsJson(body)
    }
}


class TestBody {
    String testField1
    Long testField2
}

//тест получения и ответа в виде json (не типизированный)
void getSetBodyAsJsonTest1(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertContentType('application/json')).process {
        WebApiUtilities webUtils ->
            Map requestBody = webUtils.getBodyAsJson().orElse(null) as Map
            webUtils.setBodyAsJson(['1': requestBody.testField1, '2': requestBody.testField2])
    }
}

//тест получения и ответа в виде json (типизированный)
void getSetBodyAsJsonTest2(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertContentType('application/json')).process {
        WebApiUtilities webUtils ->
            TestBody requestBody = webUtils.getBodyAsJson(TestBody.class).orElse(null)
            webUtils.setBodyAsJson(requestBody)
    }
}

//тест обязательного получения и ответа в виде json (не типизированный)
void getSetBodyAsJsonTestRequired1(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertContentType('application/json')).process {
        WebApiUtilities webUtils ->
            Map requestBody = webUtils.getBodyAsJsonElseThrow() as Map
            webUtils.setBodyAsJson(['1': requestBody.testField1, '2': requestBody.testField2])
    }
}

//тест обязательного получения и ответа в виде json (типизированный)
void getSetBodyAsJsonTestRequired2(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy().assertContentType('application/json')).process {
        WebApiUtilities webUtils ->
            TestBody requestBody = webUtils.getBodyAsJsonElseThrow(TestBody)
            webUtils.setBodyAsJson(requestBody)
    }
}

//тест  получения и ответа в виде строки
void getSetBodyAsString(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy()).process {
        WebApiUtilities webUtils ->
            String requestBody = webUtils.getBodyAsString().orElse(null)
            webUtils.setBodyAsString(requestBody)
    }
}

//тест обязательного получения и ответа в виде строки
void getSetBodyAsStringElseThrow(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy()).process {
        WebApiUtilities webUtils ->
            String requestBody = webUtils.getBodyAsStringElseThrow()
            webUtils.setBodyAsString(requestBody)
    }
}

//тест обязательного получения и ответа в виде битов
void getSetBodyAsBytes(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy()).process {
        WebApiUtilities webUtils ->
            byte[] requestBody = webUtils.getBodyAsBinary().orElse(null)
            webUtils.setBodyAsBytes(requestBody, request.getContentType())
    }
}

//тест обязательного получения и ответа в виде битов
void getSetBodyAsBytesRequired(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy()).process {
        WebApiUtilities webUtils ->
            byte[] requestBody = webUtils.getBodyAsBinaryElseThrow()
            webUtils.setBodyAsBytes(requestBody, request.getContentType())
    }
}

//тест ответа в виде битов
void setBodyAsBytesFromDtObject(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, prefs.copy()).process {
        WebApiUtilities webUtils ->
            ISDtObject file = utils.findFirst('file', ['title': op.like('%.jpeg')])
            webUtils.setBodyAsBytes(file)
    }
}

class TestBody2 {
    String aString
    Long aLong
}

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
            TestBody2 body = webUtils.getBodyAsJsonElseThrow(TestBody2)
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
            //noinspection UastIncorrectHttpHeaderInspection
            response.setHeader('message', 'Hello World!')
    }
}

void exampleGet1(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(request, response, user, new Preferences().setDatePattern("dd.MM.yyyy HH:mm:ss")).process {
        WebApiUtilities webUtils ->
            //В случае отсутствия параметра будет выкинуто исключение с заранее прописанным сообщение
            //noinspection GroovyUnusedAssignment
            String stringParam1 = webUtils.getParamElseThrow('someParam1')
            //Имеем сами возможность обработать отсутствие параметра, в данном случае выкинем исключение
            //noinspection GroovyUnusedAssignment
            String stringParam2 = webUtils.getParam('someParam2').orElseThrow { new WebApiException.BadRequest("Ты забыл указать параметр") }
            Boolean dontLikeYou = true
            if (dontLikeYou) {
                new WebApiException.Forbidden("Ты мне просто не нравишься, ухади")
            }
    }
}

String testGet1(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    return new ObjectMapper().writeValueAsString(
            [
                    'getHeaders(test)'        : request.getHeaders("test")?.toList(),
                    "getParameterMap"         : request.getParameterMap(),
                    'getParameterValues(test)': request.getParameterValues("test"),
                    'getHeader(test4)'        : request.getHeader('test4'),
                    'getIntHeader(test2)'     : request.getIntHeader('test2'),
                    'getDateHeader(test3)'    : request.getDateHeader('test3'),
                    'getContentType'          : request.getContentType(),
                    'getProtocol'             : request.getProtocol(),
                    'getServerName'           : request.getServerName(),
                    'getPathInfo'             : request.getPathInfo(),
                    'getPathTranslated'       : request.getPathTranslated(),
                    'getProperties'           : request.getProperties().collectEntries { key, value -> [key, value.toString()] },
                    'getAuthType'             : request.getAuthType(),
                    'getRemoteAddr'           : request.getRemoteAddr(),
                    'getRemoteHost'           : request.getRemoteHost(),
                    'getRemotePort'           : request.getRemotePort(),
                    'getRemoteUser'           : request.getRemoteUser(),
                    'getScheme'               : request.getScheme(),
                    'isSecure'                : request.isSecure(),
                    'getRequestURI'           : request.getRequestURI(),
                    'getRequestURL'           : request.getRequestURL(),
                    'getQueryString'          : request.getQueryString(),
                    'getUserPrincipal'        : request.getUserPrincipal()
            ]
    )
}

void exampleWithCustomExceptionWriter1(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
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

class CustomExceptionWriter implements IExceptionWriter {
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
    Preferences prefs = new Preferences().setExceptionWriter(new CustomExceptionWriter())
    RequestProcessor.create(request, response, user, prefs).process {
        WebApiUtilities webUtils ->
            throw new RuntimeException("Это исключение будет записано особым образом")
    }
}
