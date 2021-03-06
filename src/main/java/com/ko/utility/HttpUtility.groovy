package com.ko.utility

import com.ko.model.BaseEntity
import com.ko.model.Connector
import com.ko.model.PIRInfo
import com.ko.model.SonicInfo
import com.ko.model.TouchInfo
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.vertx.java.core.Vertx
import org.vertx.java.core.http.HttpClient
import org.vertx.java.core.logging.Logger

import java.text.SimpleDateFormat

class HttpUtility {

    public static enum Endpoint {
        Touchs, Pirs, Sonics
    }

    private Vertx _vertx
    private Endpoint _endpoint = Endpoint.Pirs
    private Logger _logger = StaticLogger.logger()

    public HttpUtility(Endpoint endpoint, Vertx vertx) {
        this._vertx = vertx
        this._endpoint = endpoint
    }

    public HttpClient createClient() {

        // Connect touch service
        def touchPort = Settings.getTouchPort()
        def touchHost = Settings.getTouchHost()

        // SetHost() failed if http://collector-pacific.rhcloud.com
        // * it should by @ collector-pacific.rhcloud.com
        // * https://groups.google.com/forum/#!topic/vertx/Jqm_GMc0GFY
        // * He say http url != domain name
        def client = this._vertx.createHttpClient()
        client.setPort(touchPort)
        client.setHost(touchHost)
        //client.setSSL(true)
        client.setKeepAlive(false)

        _logger.info("Create Http Client ...")
        _logger.info("Port : $touchPort")
        _logger.info("host : $touchHost")

        return client;
    }

    public String getQueryUrl() {
        if (_endpoint == Endpoint.Pirs) {
            "/pirs/query"
        } else if(_endpoint == Endpoint.Touchs) {
            "/touchs/query"
        } else {
            "/sonics/query"
        }
    }

    private String createRequestDate(Date date) {
        def format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        def dateString = format.format(date)
        return dateString
    }

    private void addToken(Object info, Date date){
        Calendar c = Calendar.getInstance()
        c.setTime(date)

        info.year = c.get(Calendar.YEAR)
        info.month = c.get(Calendar.MONTH)
        info.date = c.get(Calendar.DAY_OF_MONTH)
        info.hour = c.get(Calendar.HOUR_OF_DAY)
        info.minute = c.get(Calendar.MINUTE)
        info.second = c.get(Calendar.MINUTE)
    }

    /**
     * Process sonic json string from sensor api.
     * @param data
     * @return
     */
    public Object processSonicObject(String data) {
        List<HashMap> sonics = new JsonSlurper().parseText(data);
        sonics.each {
            def collectId = it._id.toString()
            it.remove("_id")
            def sonicString = JsonOutput.toJson(it)
            SonicInfo info = BaseEntity.$fromJson(sonicString)
            info.collectId = collectId
            addToken(info, info.enterDate)
            info.$save()
        }
        return  sonics
    }

    /**
     * Process pir json string from sensor api.
     * @param data
     * @return
     */
    public Object processPIRObject(String data){
        List<HashMap> pirs = new JsonSlurper().parseText(data)
        pirs.each {
            def collectId = it._id.toString()
            it.remove("_id")
            def pirString = JsonOutput.toJson(it)
            PIRInfo info = BaseEntity.$fromJson(pirString)
            info.collectId = collectId
            addToken(info, info.enterDate)
            info.$save()
        }
        return  pirs
    }

    public Object processTouchObject(String data) {

        def logger = StaticLogger.logger()
        logger.info("data")

        List<HashMap> touchs = new JsonSlurper().parseText(data)
        touchs.each {

            def  collectId = it._id.toString()

            // remove _id from remote
            it.remove("_id")

            // convert object to json
            def touchString = JsonOutput.toJson(it)

            // reconstuct object from json info
            TouchInfo touch = BaseEntity.$fromJson(touchString)
            touch.collectId = collectId

            // in date token
            addToken(touch, touch.touchDate)

            touch.$save()
        }
        return touchs
    }

    public String createSonicRequestString() {
        def connector = Connector.getInstance()
        def ds = connector.getDatastore()
        def sonicInfo = ds.find(SonicInfo).order("-_id").get()

        def request = new RequestObject()
        request.id = ""
        request.collectDate = ""

        def lastEnter = new Date(20,1,1)
        if(sonicInfo != null) {
            lastEnter = sonicInfo.collectDate
            request.id = sonicInfo.collectId
        }

        def dateString = createRequestDate(lastEnter)
        request.collectDate = dateString
        def requestString = BaseEntity.$toJson(request)
        return requestString
    }

    public String createPIRRequestString(){

        // Find last record
        def connector = Connector.getInstance()
        def ds = connector.getDatastore()
        def pirInfo = ds.find(PIRInfo).order("-_id").get()

        // Create request instance
        def request = new RequestObject()
        request.id = ""
        request.collectDate = ""

        // Determine condition
        def lastEnter = new Date(20,1,1)
        if(pirInfo != null) {
            lastEnter = pirInfo.collectDate
            request.id = pirInfo.collectId
        }

        // Create request string
        def dateString = createRequestDate(lastEnter)
        request.collectDate = dateString;
        def requestString = BaseEntity.$toJson(request)
        return requestString
    }

    public String createTouchRequestString() {

        // Find lass record
        def connector = Connector.getInstance()
        def ds = connector.getDatastore()
        def touchInfo = ds.find(TouchInfo).order("-_id").get()

        // Default date...
        def lastTouchDate = new Date(20, 1, 1)

        // Request object...
        def requestObject = new RequestObject()
        requestObject.id = ""

        // Use persist date
        if (touchInfo != null) {
            lastTouchDate = touchInfo.collectDate
            requestObject.id = touchInfo.collectId
        }

        // Create request string
        def dateString = createRequestDate(lastTouchDate)
        requestObject.collectDate = dateString

        def requestString = BaseEntity.$toJson(requestObject)
        return requestString;
    }
}

class RequestObject {
    String collectDate;
    String id;
}