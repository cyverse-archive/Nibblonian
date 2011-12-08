(ns nibblonian.ssl
  [:import [java.net URL]]
  [:import [java.io IOException]]
  [:import [javax.net.ssl
            HostnameVerifier
            HttpsURLConnection
            SSLContext
            SSLSession
            SSLSocketFactory
            TrustManager
            X509TrustManager]]
  [:import [java.security
            GeneralSecurityException
            SecureRandom]]
  [:import [java.security.cert
            CertificateException
            X509Certificate]])

(def trust-manager
  (proxy [X509TrustManager] []
    (getAcceptedIssuers [] nil)
    (checkClientTrusted [arg0, arg1])
    (checkServerTrusted [arg0, arg1])))

(def hostname-verifier
  (proxy [HostnameVerifier] []
    (verify [hostname, session] true)))

(def ssl-context
  (let [context (SSLContext/getInstance "SSL")]
    (do
      (. context init nil (into-array TrustManager [trust-manager]) (SecureRandom.))
      context)))

(defn- get-connection
  "Opens a connection to 'url' that doesn't care whether a cert is signed or not.
     url - Instance of java.net.URL
   Returns an open java.net.URLConnection"
  [url]
  (let [orig-socket-factory (HttpsURLConnection/getDefaultSSLSocketFactory)
        orig-hostname-verifier (HttpsURLConnection/getDefaultHostnameVerifier)]
    (try
      (do
        (HttpsURLConnection/setDefaultSSLSocketFactory (. ssl-context getSocketFactory))
        (HttpsURLConnection/setDefaultHostnameVerifier hostname-verifier)
        (. url openConnection))
      (catch GeneralSecurityException e
        (throw IOException "Unable to establish trusting SSL connection." e))
      (finally
        (do
          (HttpsURLConnection/setDefaultSSLSocketFactory orig-socket-factory)
          (HttpsURLConnection/setDefaultHostnameVerifier orig-hostname-verifier))))))

(defn input-stream [url-string]
  (let [url (URL. url-string)]
    (. (get-connection url) getInputStream)))