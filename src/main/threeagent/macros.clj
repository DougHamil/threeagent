(ns threeagent.macros)

(defmacro defcomponent [key args & body]
  `(threeagent.alpha.component/register-component-renderer! ~key (fn ~args ~@body)))
