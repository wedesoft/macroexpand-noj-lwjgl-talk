(require '[clojure.java.io :as io]
         '[clojure.math :refer (to-radians)])
(import '[javax.imageio ImageIO]
        '[org.lwjgl.glfw GLFW GLFWCursorPosCallbackI GLFWMouseButtonCallbackI]
        '[org.lwjgl.opengl GL GL11 GL13 GL15 GL20 GL30]
        '[org.lwjgl BufferUtils])

(GLFW/glfwInit)

(def window-width 1280)
(def window-height 720)

(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow window-width window-height "cube" 0 0))

(GLFW/glfwShowWindow window)
(GLFW/glfwMakeContextCurrent window)
(GL/createCapabilities)

(def mouse-pos (atom [0.0 0.0]))
(def mouse-button (atom false))

(GLFW/glfwSetCursorPosCallback
  window
  (reify GLFWCursorPosCallbackI
    (invoke
      [_this _window xpos ypos]
      (reset! mouse-pos [xpos ypos]))))

(GLFW/glfwSetMouseButtonCallback
  window
  (reify GLFWMouseButtonCallbackI
    (invoke
      [_this _window _button action _mods]
      (reset! mouse-button (= action GLFW/GLFW_PRESS)))))

(def vertex-source "
#version 130

in vec3 point;

void main()
{
  gl_Position = vec4(point, 1);
}")

(def fragment-source "
#version 130

uniform vec2 iResolution;
uniform sampler2D moon;
out vec4 fragColor;

void main()
{ 
  fragColor = texture(moon, gl_FragCoord.xy / iResolution.xy);
}")

(defn make-shader [source shader-type]
  (let [shader (GL20/glCreateShader shader-type)]
    (GL20/glShaderSource shader source)
    (GL20/glCompileShader shader)
    (when (zero? (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS))
      (throw (Exception. (GL20/glGetShaderInfoLog shader 1024))))
    shader))

(defn make-program [& shaders]
  (let [program (GL20/glCreateProgram)]
    (doseq [shader shaders]
           (GL20/glAttachShader program shader)
           (GL20/glDeleteShader shader))
    (GL20/glLinkProgram program)
    (when (zero? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
      (throw (Exception. (GL20/glGetProgramInfoLog program 1024))))
    program))

(def vertex-shader (make-shader vertex-source GL20/GL_VERTEX_SHADER))
(def fragment-shader (make-shader fragment-source GL20/GL_FRAGMENT_SHADER))
(def program (make-program vertex-shader fragment-shader))

(def vertices
  (float-array [ 1.0  1.0 0.0
                -1.0  1.0 0.0
                -1.0 -1.0 0.0
                 1.0 -1.0 0.0]))

(def indices
  (int-array [0 1 2 3]))

(defmacro def-make-buffer [method create-buffer]
  `(defn ~method [data#]
     (let [buffer# (~create-buffer (count data#))]
       (.put buffer# data#)
       (.flip buffer#)
       buffer#)))

(def-make-buffer make-float-buffer BufferUtils/createFloatBuffer)
(def-make-buffer make-int-buffer BufferUtils/createIntBuffer)
(def-make-buffer make-byte-buffer BufferUtils/createByteBuffer) 

(defn setup-vao [vertices indices]
  (let [vao (GL30/glGenVertexArrays)
        vbo (GL15/glGenBuffers)
        ibo (GL15/glGenBuffers)]
    (GL30/glBindVertexArray vao)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
    (GL15/glBufferData GL15/GL_ARRAY_BUFFER (make-float-buffer vertices)
                       GL15/GL_STATIC_DRAW)
    (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER ibo)
    (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER (make-int-buffer indices)
                       GL15/GL_STATIC_DRAW)
    {:vao vao :vbo vbo :ibo ibo}))

(def vao (setup-vao vertices indices))

(def point (GL20/glGetAttribLocation program "point"))
(GL20/glVertexAttribPointer point 3 GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
(GL20/glEnableVertexAttribArray point)

(def color (ImageIO/read (io/file "lroc_color_poles_2k.tif")))
(def color-raster (.getRaster color))
(def color-width (.getWidth color-raster))
(def color-height (.getHeight color-raster))
(def color-channels (.getNumBands color-raster))
(def color-pixels (int-array (* color-width color-height color-channels)))
(.getPixels color-raster 0 0 color-width color-height color-pixels)
[color-width color-height color-channels]

(def texture-color (GL11/glGenTextures))
(GL11/glBindTexture GL11/GL_TEXTURE_2D texture-color)
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA color-width color-height 0
                   GL11/GL_RGB GL11/GL_UNSIGNED_BYTE
                   (make-byte-buffer (byte-array (map unchecked-byte color-pixels))))
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT)
(GL11/glBindTexture GL11/GL_TEXTURE_2D 0)

(GL20/glUseProgram program)
(GL20/glUniform2f (GL20/glGetUniformLocation program "iResolution") window-width window-height)
(GL20/glUniform1i (GL20/glGetUniformLocation program "moon") 0)
(GL13/glActiveTexture GL13/GL_TEXTURE0)
(GL11/glBindTexture GL11/GL_TEXTURE_2D texture-color)

(while (not (GLFW/glfwWindowShouldClose window))
       (GL11/glClearColor 0.0 0.0 0.0 1.0)
       (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
       (GL11/glDrawElements GL11/GL_QUADS (count indices) GL11/GL_UNSIGNED_INT 0)
       (GLFW/glfwSwapBuffers window)
       (GLFW/glfwPollEvents))

(defn teardown-vao [{:keys [vao vbo ibo]}]
  (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers ibo)
  (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers vbo)
  (GL30/glBindVertexArray 0)
  (GL15/glDeleteBuffers vao))

(teardown-vao vao)

(GL20/glDeleteProgram program)
(GL11/glDeleteTextures texture-color)

(GLFW/glfwDestroyWindow window)
(GLFW/glfwTerminate)
