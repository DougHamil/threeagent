(ns threeagent.impl.component-macros)

(defmacro defrenderer [key args & body]
  `(threeagent.impl.component/register-component-renderer! ~key (fn ~args ~@body)))
