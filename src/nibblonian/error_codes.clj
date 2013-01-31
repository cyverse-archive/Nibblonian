(ns nibblonian.error-codes
  (use [slingshot.slingshot :only [try+]])
  (require [cheshire.core :as cheshire]
           [clojure.tools.logging :as log]))

(def ERR_DOES_NOT_EXIST          "ERR_DOES_NOT_EXIST")
(def ERR_EXISTS                  "ERR_EXISTS")
(def ERR_NOT_WRITEABLE           "ERR_NOT_WRITEABLE")
(def ERR_NOT_READABLE            "ERR_NOT_READABLE")
(def ERR_WRITEABLE               "ERR_WRITEABLE")
(def ERR_READABLE                "ERR_READABLE")
(def ERR_NOT_A_USER              "ERR_NOT_A_USER")
(def ERR_NOT_A_FILE              "ERR_NOT_A_FILE")
(def ERR_NOT_A_FOLDER            "ERR_NOT_A_FOLDER")
(def ERR_IS_A_FILE               "ERR_IS_A_FILE")
(def ERR_IS_A_FOLDER             "ERR_IS_A_FOLDER")
(def ERR_INVALID_JSON            "ERR_INVALID_JSON")
(def ERR_BAD_OR_MISSING_FIELD    "ERR_BAD_OR_MISSING_FIELD")
(def ERR_NOT_AUTHORIZED          "ERR_NOT_AUTHORIZED")
(def ERR_MISSING_QUERY_PARAMETER "ERR_MISSING_QUERY_PARAMETER")
(def ERR_MISSING_FORM_FIELD      "ERR_MISSING_FORM_FIELD")
(def ERR_NOT_AUTHORIZED          "ERR_NOT_AUTHORIZED")
(def ERR_MISSING_QUERY_PARAMETER "ERR_MISSING_QUERY_PARAMETER")
(def ERR_INCOMPLETE_DELETION     "ERR_INCOMPLETE_DELETION")
(def ERR_INCOMPLETE_MOVE         "ERR_INCOMPLETE_MOVE")
(def ERR_INCOMPLETE_RENAME       "ERR_INCOMPLETE_RENAME")
(def ERR_REQUEST_FAILED          "ERR_REQUEST_FAILED")
(def ERR_UNCHECKED_EXCEPTION     "ERR_UNCHECKED_EXCEPTION")

(defn error? [obj] (contains? obj :error_code))

(defn unchecked [throwable-map]
  {:error_code ERR_UNCHECKED_EXCEPTION
   :message (:message throwable-map)})

(defn err-resp [action err-obj]
  {:status 500
   :body (-> err-obj
           (assoc :action action)
           (assoc :status "failure")
           cheshire/encode)})

(defn success-resp [action retval]
  (if (= (:status retval) 200)
    retval
    {:status 200
     :body
     (cond
       (map? retval)
       (-> retval
         (assoc :status "success"
                :action action)
         cheshire/encode)

       (not (string? retval))
     (.toString retval)

     :else retval)}))

(defn format-exception
  "Formats the exception as a string."
  [exception]
  (log/debug "format-exception")
  (let [string-writer (java.io.StringWriter.)
        print-writer  (java.io.PrintWriter. string-writer)]
    (.printStackTrace exception print-writer)
    (str string-writer)))

(defn trace-element-str
  [trace-elems]
  (let [sb (StringBuilder.)]
    (doseq [te trace-elems]
      (.append sb (str te "\n")))
    (str sb)))

(defn trap [action func & args]
  (try+
    (success-resp action (apply func args))
    (catch error? err
      (log/error (str err "\n" (trace-element-str (:stack-trace &throw-context))))
      (err-resp action err))
    (catch java.lang.Exception e
      (log/error e)
      (err-resp action (unchecked &throw-context)))))
