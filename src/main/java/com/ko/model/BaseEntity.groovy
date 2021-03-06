package com.ko.model

import com.google.code.morphia.annotations.Id
import com.google.code.morphia.annotations.Transient
import com.google.code.morphia.query.UpdateOperations
import com.ko.utility.StaticLogger
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.bson.types.ObjectId
import org.vertx.java.core.logging.Logger

import java.text.SimpleDateFormat

/**
 * Created by recovery on 12/22/13.
 */
class BaseEntity<T> implements Serializable {

    /**
     * Default class logger.
     * User logger from vert.x
     */
    @Transient
    private static Logger _logger = StaticLogger.logger()

    /**
     * Unique mongodb id.
     */
    @Id
    ObjectId _id

    /**
     * Object identifier reflect from _id.
     */
    @Transient
    String identifier

    /**
     *  Archive data (hide this object from client access).
     */
    Date archiveDate;
    boolean archive = false;

    /**
     * Delete date.
     */
    Date deleteDate;
    boolean delete = false;

    /**
     * Create date.
     */
    Date createDate // = Calendar.getInstance().getTime();
    String createBy;

    /**
     * Update date.
     */
    Date lastUpdate // = Calendar.getInstance().getTime();
    String updateBy;

    /**
     * Default database connector.
     */
    @Transient
    protected static Connector _connector = Connector.getInstance()

    /**
     * Get update operation object.
     * @param cls
     * @return
     */
    def UpdateOperations getUpdateOps(Class cls) {
        return _connector.getDatastore().createUpdateOperations(cls)
    }

    /**
     * Save entity object into database.
     * @return
     */
    def Result $save() {
        try {
            if (this._id == null) {
                this.createDate = Calendar.getInstance().getTime()
            } else {
                // def fmt = "2014-02-03T07:56:14+0000"
                def dateForm = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            }

            this.lastUpdate = Calendar.getInstance().getTime();

            _connector.getDatastore().save(this)

            this.identifier = this._id.toString()
            return new Result(success: true, id: this._id.toString())
        } catch (e) {
            return new Result(success: false, message: e.getMessage())
        }
    }

    /**
     * Delete current object from database.
     * @param cls
     * @return
     */
    def Result $remove(Class cls) {
        def rs = _connector.getDatastore().delete(this);

        if (rs != null) {
            return new Result(success: true, data: rs);
        } else {
            return new Result(success: false, data: rs)
        }
    }

    /**
     * Find all given entity class from database.
     * @param cls
     * @return
     */
    def static <T> List<T> $findAll(Class cls) {

        _logger.trace("Find All: " + cls.name)

        try {
            def rs = _connector.getDatastore().createQuery(cls).asList()
            rs.each { BaseEntity d -> d.identifier = d._id.toString() }

            _logger.info("Before Filter: " + rs.size())

            rs = rs.findAll { !it.delete }.iterator().toList()

            _logger.info("Alfter Filter: " + rs.size())

            return rs
        } catch (e) {
            return new ArrayList<T>()
        }
    }

    /**
     * Find object by id.
     * @param cls
     * @param id
     * @return
     */
    def static Object $findById(Class cls, ObjectId id) {

//        _logger.info("Find By Id:" + id.toString())
//        _logger.info("Class: " + cls.name)

        def entry = _connector.getDatastore().get(cls, id)
        if (entry != null) {
            entry.identifier = entry._id.toString()
        }
        return entry
    }

    /**
     * Query entity class by given conditions.
     * @param cls
     * @param condition
     * @return
     */
    def static <T> T $queryBy(Class cls, HashMap<String, Object> condition) {

        def db = _connector.getDatastore()
        def con = db.createQuery(cls)

        condition.each { k, v ->
            _logger.info("== Key: " + k)
            _logger.info("== Value: " + v)
//            con.criteria(k).equals(v)
            con.field(k).equal(v)
        }

        con.get()
    }

    /**
     * Find single entity class by example.
     * @param example
     * @return
     */
    def static <T> T $findByExample(T example) {
        try {

            def customer = _connector.getDatastore().queryByExample(example).get()
            customer.each { BaseEntity c -> c.identifier = c._id.toString() }

            _logger.info("Find By Example: " + customer._id.toString())

            return customer
        } catch (e) {

            _logger.error(e.getMessage())
            _logger.error(e.stackTrace)

            return null
        }
    }

    /**
     * Find all entity class by example.
     * @param example
     * @return
     */
    def static <T> T $findAllByExample(T example) {
        def rs = _connector.getDatastore().queryByExample(example).asList()
        rs.each { BaseEntity c -> c.identifier = c._id.toString() }
        return rs;
    }

    /**
     * Convert current object into json string.
     * @return
     */
    def String $toJson() {
        return $toJson(this);
    }

    /**
     * Convert arbitrary object into json string.
     * @param obj
     * @return
     */
    def static String $toJson(Object obj) {

        def out = JsonOutput.toJson(obj)
        out = JsonOutput.prettyPrint(out)

        _logger.info("== Serialize ==")
        _logger.info("Class: " + obj.class)

//        def out = JSON.serialize(obj)

        return out
    }

    /**
     * Parse date string into native java date.
     * @param obj
     * @param name
     */
    def static void pareDate(Object obj, String name) {
        try {
            // Parse date string int java native date
            // 2014-02-03T08:46:22+0000 -- from java
            // 2014-02-21T08:42:52.925Z -- from mongojs
            def dateForm = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
            obj."$name" = dateForm.parse(obj."$name")

        } catch (e) {
            try {
                //_logger.error("<< Parse Step Two >>")
                def fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                obj."$name" = fmt.parse(obj."$name")

            }catch(ex){
                _logger.error("== Parse <<$name>> Failed==")
                //_logger.error(e)
            }
        }
    }

    /**
     * Convert json string into dynamic object.
     * @param json
     * @return
     */
    def static Object $fromJson(String json) {
        //def obj = JSON.parse(json)
        //removeExtraProperty(obj)

        def obj = $fromJsonSluper(json)

        pareDate(obj, "deleteDate")
        pareDate(obj, "archiveDate")
        pareDate(obj, "createDate")
        pareDate(obj, "lastUpdate")

        pareDate(obj, "touchDate")
        pareDate(obj, "collectDate")

        pareDate(obj, "enterDate")
        pareDate(obj, "leaveDate")

        try {
            obj._id = new ObjectId(obj.identifier)
        } catch(e) {}

        return obj;
    }

    /**
     * Remove unneed extra property from object.
     * @param rs
     */
    def private static removeExtraProperty(HashMap rs) {
        def itor = rs.entrySet().iterator()
        while (itor.hasNext()) {
            Map.Entry<String, Object> entry = itor.next();

            if (entry.key.contains("\$") || entry.key.startsWith("_")) {

                _logger.info("Remove: " + entry.key)
                itor.remove();
            }
        }
    }

    /**
     * Convert json into object using JsonSlurper (groovy class).
     * @param json
     * @return
     */
    def static Object $fromJsonSluper(String json) {
//        json = json.replaceAll("\\\$\\\$hashKey", "xxx")

        HashMap rs = new JsonSlurper().parseText(json)
        removeExtraProperty(rs)

        return rs
    }

//    def static Object $fromJson(String json, Class cls) {
//        def gson = new Gson()
//        def rs = gson.fromJson(json, cls)
//        return rs
//    }
}
