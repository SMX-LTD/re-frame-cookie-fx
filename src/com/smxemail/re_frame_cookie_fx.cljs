(ns com.smxemail.re-frame-cookie-fx
  (:require
    [cljs.spec.alpha :as s]
    [goog.net.cookies]
    [re-frame.core :refer [console dispatch reg-cofx reg-fx]]))

;; A coeffect handler that injects the state of cookies being enabled or
;; disabled.

(reg-cofx
  :cookie/enabled?
  (fn [coeffects]
    (assoc
     coeffects
      :cookie/enabled?
      (.isEnabled goog.net.cookies))))

;; A coeffect handler that injects whether there are any cookies for this
;; document.

(reg-cofx
  :cookie/empty?
  (fn [coeffects]
    (assoc
     coeffects
      :cookie/empty?
      (.isEmpty goog.net.cookies))))

;; A coeffect handler that injects the cookies value(s) associated with the
;; given name(s).

(reg-cofx
  :cookie/get
  (fn [coeffects names]
    (assoc
     coeffects
      :cookie/get
      (reduce #(into %1 {(keyword %2) (.get goog.net.cookies (name %2))}) {} names))))

;; A coeffect handler that injects the names for all the cookies.

(reg-cofx
  :cookie/keys
  (fn [coeffects]
    (assoc
     coeffects
      :cookie/keys
      (.getKeys goog.net.cookies))))

;; A coeffect handler that injects the values for all the cookies.

(reg-cofx
  :cookie/values
  (fn [coeffects]
    (assoc
     coeffects
      :cookie/values
      (.getValues goog.net.cookies))))

;; A coeffect handler that injects the number of cookies for this document.

(reg-cofx
  :cookie/count
  (fn [coeffects]
    (assoc
     coeffects
      :cookie/count
      (.getCount goog.net.cookies))))


(s/def ::sequential-or-map (s/or :list-or-vector sequential? :map map?))

;; An effects handler that actions setting cookies.
;;
;; To set a cookie supply a map or vector of maps using the following options:
;;   :name The cookie name.
;;   :value The cookie value.
;;   :max-age The max age in seconds (from now). Use -1 to set a session cookie,
;;            which is the default.
;;   :path The path of the cookie. If not present then uses /.
;;   :domain The domain of the cookie, or nil to not specify a domain attribute
;;           in which case the browser will use the full request host name.
;;   :secure Whether the cookie should only be sent over a secure channel.
;;   :on-success
;;   :on-failure
;;
;;  Note: Neither the name or value are encoded in any way. It is up to the
;;        caller to handle any possible encoding and decoding.
(reg-fx
  :cookie/set
  (fn cookie-set-effect [options]
    (when (= :cljs.spec.alpha/invalid (s/conform ::sequential-or-map options))
      (console :error (s/explain-str ::sequential-or-map options)))
    (cond
      (sequential? options)
      (run! cookie-set-effect options)
      (map? options)
      (let [{:keys [name value max-age path domain secure on-success on-failure]
             :or   {max-age    -1
                    path       "/"
                    on-success [:cookie-set-no-on-success]
                    on-failure [:cookie-set-no-on-failure]}} options
            sname (cljs.core/name name)]
        (cond
          (not (.isValidName goog.net.cookies sname))
          (dispatch (conj on-failure (ex-info options "cookie name fails #goog.net.cookies.isValidName")))
          (not (.isValidValue goog.net.cookies value))
          (dispatch (conj on-failure (ex-info options "cookie value fails #goog.net.cookies.isValidValue")))
          true
          (try
            (.set goog.net.cookies sname value max-age path domain secure)
            (dispatch (conj on-success options))
            (catch :default e
              (dispatch (conj on-failure e)))))))))

;; An effects handler that actions removing cookies.
;;
;; To remove a cookie supply a map or vector of maps using the following options:
;;   :name The cookie name.
;;   :path The path of the cookie. If not present then uses /.
;;   :domain The domain of the cookie, or nil to not specify a domain attribute
;;           in which case the browser will use the full request host name.
;;   :on-success
;;   :on-failure
(reg-fx
  :cookie/remove
  (fn cookie-remove-effect [options]
    (when (= :cljs.spec.alpha/invalid (s/conform ::sequential-or-map options))
      (console :error (s/explain-str ::sequential-or-map options)))
    (cond
      (sequential? options)
      (run! cookie-remove-effect options)
      (map? options)
      (let [{:keys [name path domain on-success on-failure]
             :or   {path       "/"
                    on-success [:cookie-remove-no-on-success]
                    on-failure [:cookie-remove-no-on-failure]}} options
            sname (cljs.core/name name)]
        (if (not (.isValidName goog.net.cookies sname))
          (dispatch (conj on-failure (ex-info options "cookie name fails #goog.net.cookies.isValidName")))
          (try
            (.remove goog.net.cookies sname path domain)
            (dispatch (conj on-success options))
            (catch :default e
              (dispatch (conj on-failure e)))))))))

;; An effects handler that eats all the cookies... Om nom nom nom.
;;
;; Removes all cookies for this document. Note that this will only remove
;; cookies from the current path and domain. If there are cookies set using a
;; subpath and/or another domain these will still be there.
(reg-fx
  :cookie/clear
  (fn [{:keys [on-success on-failure]}]
    (try
      (.clear goog.net.cookies)
      (dispatch on-success)
      (catch :default e
        (dispatch (conj on-failure e))))))
