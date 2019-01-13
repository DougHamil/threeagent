(ns threeagent.component-macros)

(defmacro defrenderer [key args & body]
  `(threeagent.component/register-component-renderer! ~key (fn ~args ~@body)))
