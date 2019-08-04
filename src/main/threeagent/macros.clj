(ns threeagent.macros)

(defmacro defcomponent [key args & body]
  `(threeagent.component/register-component-renderer! ~key (fn ~args ~@body)))
