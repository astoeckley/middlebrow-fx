(ns middlebrow-fx.core
  (:require [middlebrow.core :refer :all])
  (:import [javafx.stage Stage StageStyle]
           [javafx.application Platform Application]
           [javafx.scene.web WebView]
           [javafx.scene Scene]
           [javafx.event EventHandler]
           [javafx.beans.value ChangeListener]
           [com.sun.javafx.application PlatformImpl]))

(defmacro run-async [& body]
  `(Platform/runLater (fn [] ~@body)))

(defmacro run-sync [& body]
  `(let [p# (promise)]
     (Platform/runLater #(deliver p# (do ~@body)))
     @p#))

(defmacro call [call-type & body]
  `(case ~call-type
     :direct ~@body
     :sync (run-sync ~@body)
     :async (run-async ~@body)))

(defrecord FXBrowser [stage web-view call-type]
  IBrowser
  (show [self]
    (call call-type
      (.show stage)))

  (hide [self]
    (call call-type
      (.hide stage)))

  (activate [self]
    ; TODO: This doesn't seem to truly give focus to the window on OSX.
    ; It only puts the window above other windows, which isn't quite what
    ; we want. JavaFX doesn't seem to support this operation directly,
    ; but is there some kind of workaround we could use?
    (call call-type
      (.requestFocus stage)))

  (deactivate [self]
    (call call-type
      (.toBack stage)))

  (close [self]
    (call call-type
      (.close stage)))

  (visible? [self]
    (.isShowing stage))

  (minimize [self]
    (call call-type
      (.setIconified stage true)))

  (maximize [self]
    (call call-type
      (.setMaximized stage true)))

  (minimized? [self]
    (call call-type
      (.isIconified stage)))

  (maximized? [self]
    (call call-type
      (.isMaximized stage)))

  (set-fullscreen [self fullscreen]
    (call call-type
      (.setFullScreen stage fullscreen)))

  (fullscreen? [self]
    (call call-type
      (.isFullScreen stage)))

  (get-title [self]
    (.getTitle stage))

  (set-title [self title]
    (call call-type
      (.setTitle stage title)))

  (get-x [self]
    (.getX stage))

  (set-x [self x]
    (.setX stage x))

  (get-y [self]
    (.getY stage))

  (set-y [self y]
    (.setY stage y))

  (get-position [self]
    [(.getX self) (.getY self)])

  (set-position [self position]
    (let [[x y] position]
      (.setX stage x)
      (.setY stage y)))

  (set-position [self x y]
    (.setX stage x)
    (.setY stage y))

  (get-width [self]
    (.getWidth stage))

  (set-width [self width]
    (.setWidth stage width))

  (get-height [self]
    (.getHeight stage))

  (set-height [self height]
    (.setHeight stage height))

  (get-size [self]
    [(.getWidth stage) (.getHeight stage)])

  (set-size [self size]
    (let [[width height] size]
      (set-size self width height)))

  (set-size [self width height]
    (.setWidth stage width)
    (.setHeight stage height))

  (get-url [self]
    (-> web-view (.getEngine) (.getLocation)))

  (set-url [self url]
    (call call-type
      (-> web-view (.getEngine) (.load url))))

  (container-type [self] :fx)

  (start-event-loop [self])
  (start-event-loop [self error-fn])

  (listen-closed [self handler]
    (.setOnCloseRequest stage
      (proxy [EventHandler] []
        (handle [e]
          (handler e)))))

  (listen-focus-gained [self handler]
    (-> stage (.focusedProperty)
      (.addListener
        (proxy [ChangeListener] []
          (changed [observable old new]
            (when new
              (handler {})))))))

  (listen-focus-lost [self handler]
    (-> stage (.focusedProperty)
      (.addListener
        (proxy [ChangeListener] []
          (changed [observable old new]
            (when-not new
              (handler {}))))))))

(defn style->stage-style [style]
  (case style
    :normal StageStyle/DECORATED
    :undecorated StageStyle/UNDECORATED
    :transparent StageStyle/TRANSPARENT
    :tool StageStyle/UTILITY
    :unified StageStyle/UNIFIED
    style))

(defn create-window
  "Creates a new browser window with the specified options."
  [& {:keys [title url x y width height style call-type]
      :as   opts}]
  (PlatformImpl/startup #())
  (Platform/setImplicitExit false)
  (let [window-promise (promise)]
    (Platform/runLater
      #(let [stage (Stage.)]
        (-> (proxy [Application] []
              (start [stage]
                (let [web-view (WebView.)
                      scene (Scene. web-view (or width 400) (or height 300))]
                  (.setScene stage scene)
                  (when style (.initStyle stage (style->stage-style style)))
                  (when x (.setX stage x))
                  (when y (.setY stage y))
                  (.setTitle stage (or title "Untitled"))
                  (-> web-view (.getEngine) (.load (or url "about:blank")))
                  (deliver window-promise
                    (->FXBrowser stage web-view (or call-type :sync))))))
          (.start stage))))
    @window-promise))