(ns nibblonian.request-utils
  (:use [nibblonian.error-codes])
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [ring.util.response :as rsp-utils]))

(defn json?
  "Checks to make sure that a string contains JSON."
  [a-string]
  (log/debug (str "json? " a-string))
  (if (try
        (json/read-json a-string)
        (catch Exception e false))
    true false))

(defn invalid-fields
  "Validates the format of a map against a spec.

   map-spec is a map where the key is the name of a
   corresponding field in a-map that must exist. The
   value is a function that returns true or false
   when the corresponding value in a-map is passed into
   it.

   Returns a sequence of field-names from 'a-map'that
   aren't compliant with the spec. They're either missing
   or the validator function returned false when the
   value was passed in."
  [a-map map-spec]
  (log/debug (str "invalid-fields " a-map " " map-spec))
  (filter (comp not nil?)
          (for [[field-name validator?] map-spec]
            (if (contains? a-map field-name)
              (if (validator? (get a-map field-name)) nil field-name)
              field-name))))

(defn map-is-valid?
  "Returns true if the 'a-map' conforms to 'map-spec'."
  [a-map map-spec]
  (log/debug (str "map-is-valid? " a-map " " map-spec))
  (if (map? a-map)
    (== 0 (count (invalid-fields a-map map-spec)))
    false))

(defn parse-json
  "Parses a JSON string into a map. Performs error-checking."
  [json-string map-spec]
  (log/debug (str "parse-json " json-string " " map-spec))
  (if (json? json-string)
    (let [obj (json/read-json json-string)]
      (if (map-is-valid? obj map-spec)
        obj
        {:status "failure"
         :reason "Bad or missing field"
         :error_code ERR_BAD_OR_MISSING_FIELD
         :fields (invalid-fields obj map-spec)}))
    {:status "failure" 
     :reason "Invalid JSON" 
     :error_code ERR_INVALID_JSON}))

(defn query-param
  "Grabs the 'field' from the query string associated
   with the request and returns it.

   Parameters:
      request - request map put together by Ring.
      field - name of the query value to return.
   "
  [request field]
  (log/debug (str "query-param " field))
  (get (:query-params request) field))

(defn query-param?
  "Checks to see if the specified query-param actually exists
   in the request.

   Parameters:
      request - request map put together by Ring.
      field - name of the query key to check for."
  [request field]
  (log/debug (str "query-param?" field))
  (contains? (:query-params request) field))

(defn form-param
  "Grabs the 'field' from the form-data associated with
   the request and returns it.

   Parameters:
     request - request map put together by Ring.
     field - name of the form-data value to return."
  [request field]
  (log/debug (str "form-param " field))
  (get (:params request) field))

(defn is-failed?
  "Checks the map 'result-msg' to see if it represents
   a failed jargon-core call."
  [result-msg]
  (log/debug (str "is-failed? " result-msg))
  (= "failure" (:status result-msg)))

(defn create-response
  "Creates a Ring-compatible response map from the 'results' map returned
   by the calls into irods."
  ([results] (create-response results "text/plain"))
  ([results content-type]
    (log/debug (str "create-response " results))
    (let [status (if (not (is-failed? results)) 200 400)
          body (json/json-str results)
          retval (merge
                   (rsp-utils/content-type (rsp-utils/response "") content-type)
                   {:status status :body body})]
      (log/info (str "Returning " (json/json-str retval)))
      retval)))

(defn valid-body?
  [request body-spec]
  (cond
    (not (map? (:body request)))
    false
    
    (not (map-is-valid? (:body request) body-spec))
    false
    
    :else
    true))

(defn bad-body 
  [request body-spec]
  (cond
    (not (map? (:body request)))
    {:status "failure"
     :action "body-check"
     :error_code ERR_INVALID_JSON
     :reason "Invalid JSON"}
    
    (not (map-is-valid? (:body request) body-spec))
    {:status "failure"
     :reason "Bad or missing field"
     :error_code ERR_BAD_OR_MISSING_FIELD
     :fields (invalid-fields  (:body request) body-spec)}
    
    :else
    {:status "success"}))

(defn bad-query [key action]
  (create-response
    {:action action
     :status "failure"
     :error_code ERR_MISSING_QUERY_PARAMETER
     :reason (str "missing " key " query parameter")}))

(defn fail-resp
  [action status reason]
  (create-response {:action action :status status :reason reason}))

(defn multipart-inputfile
  "Extracts the location of an uploaded files temp location from
   the Ring request map."
  [request]
  (log/debug "multipart-inputfile")
  (:tempfile (form-param request "file")))

(defn attachment?
  [request]
  (if (not (query-param? request "attachment"))
    true
    (let [disp (query-param request "attachment")]
      (if (= "1" disp)
        true
        false))))
