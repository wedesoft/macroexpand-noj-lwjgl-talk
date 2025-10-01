(require '[clojure.java.io :as io]
         '[clojure.math :refer (PI to-radians)]
         '[fastmath.vector :refer (vec3 sub add mult normalize)])
(import '[javax.imageio ImageIO]
        '[org.lwjgl.glfw GLFW GLFWCursorPosCallbackI GLFWMouseButtonCallbackI]
        '[org.lwjgl.opengl GL GL11 GL13 GL15 GL20 GL30]
        '[org.lwjgl BufferUtils])

(GLFW/glfwInit)

(def window-width 1280)
(def window-height 720)
(def radius 1737.4)

(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow window-width window-height "moon" 0 0))

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

#define M_PI 3.1415926535897932384626433832795

uniform float fov;
uniform float distance;
uniform vec2 iResolution;
uniform vec2 iMouse;
in vec3 point;
out vec3 vpoint;

void main()
{
  float alpha = iMouse.x / iResolution.x * M_PI * 2.0 + M_PI;
  float beta = (iMouse.y / iResolution.y - 0.5) * M_PI * 2.0;
  mat3 rot_y = mat3(vec3(cos(alpha), 0, sin(alpha)),
                    vec3(0, 1, 0),
                    vec3(-sin(alpha), 0, cos(alpha)));
  mat3 rot_x = mat3(vec3(1, 0, 0),
                    vec3(0, cos(beta), -sin(beta)),
                    vec3(0, sin(beta), cos(beta)));
  vec3 p = rot_x * rot_y * point + vec3(0, 0, distance);
  float f = 1.0 / tan(fov / 2.0);
  float aspect = iResolution.x / iResolution.y;
  float proj_x = p.x / p.z * f;
  float proj_y = p.y / p.z * f * aspect;
  float proj_z = p.z / (2.0 * distance);
  gl_Position = vec4(proj_x, proj_y, proj_z, 1);
  vpoint = point;
}")

(def uv-source "
#version 130

#define PI 3.1415926535897932384626433832795

vec2 uv(vec3 p)
{
  float u = atan(p.x, -p.z) / (2.0 * PI) + 0.5;
  float v = 0.5 - atan(p.y, length(p.xz)) / PI;
  return vec2(u, v);
}")

(def oriented-matrix-source "
#version 130

vec3 orthogonal_vector(vec3 n)
{
  vec3 b;
  if (abs(n.x) <= abs(n.y)) {
    if (abs(n.x) <= abs(n.z))
      b = vec3(1, 0, 0);
    else
      b = vec3(0, 0, 1);
  } else {
    if (abs(n.y) <= abs(n.z))
      b = vec3(0, 1, 0);
    else
      b = vec3(0, 0, 1);
  };
  return normalize(cross(n, b));
}

mat3 oriented_matrix(vec3 n)
{
  vec3 o1 = orthogonal_vector(n);
  vec3 o2 = cross(n, o1);
  return mat3(n, o1, o2);
}")

(def color-source "
#version 130

uniform sampler2D moon;

vec3 color(vec2 uv)
{
  return texture(moon, uv).rgb;
}")

(def elevation-source "
#version 130

uniform sampler2D ldem;

vec2 uv(vec3 p);

float elevation(vec3 p)
{
  return texture(ldem, uv(p)).r;
}")

(def fragment-source "
#version 130

in vec3 vpoint;
out vec4 fragColor;

vec2 uv(vec3 p);
vec3 color(vec2 uv);

void main()
{
  fragColor = vec4(color(uv(vpoint)), 1);
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
(def uv-shader (make-shader uv-source GL20/GL_FRAGMENT_SHADER))
(def color-shader (make-shader color-source GL20/GL_FRAGMENT_SHADER))
(def fragment-shader (make-shader fragment-source GL20/GL_FRAGMENT_SHADER))
(def program (make-program vertex-shader uv-shader color-shader fragment-shader))

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

(def points
  [(vec3 -1.0 -1.0 -1.0)
   (vec3  1.0 -1.0 -1.0)
   (vec3 -1.0  1.0 -1.0)
   (vec3  1.0  1.0 -1.0)
   (vec3 -1.0 -1.0  1.0)
   (vec3  1.0 -1.0  1.0)
   (vec3 -1.0  1.0  1.0)
   (vec3  1.0  1.0  1.0)])

(def quads
  [[0 1 3 2]
   [6 7 5 4]
   [0 2 6 4]
   [5 7 3 1]
   [2 3 7 6]
   [4 5 1 0]])

(def corners
  (map (fn [[i _ _ _]] (nth points i))
       quads))

(def u-vectors
  (map (fn [[i j _ _]] (sub (nth points j) (nth points i)))
       quads))

(def v-vectors
  (map (fn [[i _ _ l]] (sub (nth points l) (nth points i)))
       quads))

(defn sphere-points [n c u v]
  (for [j (range (inc n)) i (range (inc n))]
       (-> c
           (add (mult u (/ i n)))
           (add (mult v (/ j n)))
           normalize
           (mult radius ))))

(defn sphere-indices [n face]
  (for [j (range n) i (range n)]
       (let [offset (+ (* face (inc n) (inc n)) (* j (inc n)) i)]
         [offset (+ offset 1) (+ offset (inc n) 1) (+ offset (inc n))])))

(def n 16)

(def vertices
  (float-array
    (flatten
      (map (partial sphere-points n)
           corners u-vectors v-vectors))))

(def indices
  (int-array
    (flatten
      (map (partial sphere-indices n)
           (range 6)))))

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

(def ldem (ImageIO/read (io/file "ldem_4.tif")))
(def ldem-raster (.getRaster ldem))
(def ldem-width (.getWidth ldem))
(def ldem-height (.getHeight ldem))
(def ldem-pixels (float-array (* ldem-width ldem-height)))
(.getPixels ldem-raster 0 0 ldem-width ldem-height ldem-pixels) nil
(def resolution (/ (* 2.0 PI radius) ldem-width))

(def texture-ldem (GL11/glGenTextures))
(GL11/glBindTexture GL11/GL_TEXTURE_2D texture-ldem)
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL30/GL_R32F ldem-width ldem-height 0
                   GL11/GL_RED GL11/GL_FLOAT ldem-pixels)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT)

(GL20/glUseProgram program)
(GL20/glUniform2f (GL20/glGetUniformLocation program "iResolution") window-width window-height)
(GL20/glUniform1f (GL20/glGetUniformLocation program "fov") (to-radians 35.0))
(GL20/glUniform1f (GL20/glGetUniformLocation program "distance") (* radius 10.0))
(GL20/glUniform1i (GL20/glGetUniformLocation program "moon") 0)
(GL13/glActiveTexture GL13/GL_TEXTURE0)
(GL11/glBindTexture GL11/GL_TEXTURE_2D texture-color)

(GL11/glEnable GL11/GL_CULL_FACE)
(GL11/glCullFace GL11/GL_BACK)

(while (not (GLFW/glfwWindowShouldClose window))
       (when @mouse-button
         (GL20/glUniform2f (GL20/glGetUniformLocation program "iMouse") (@mouse-pos 0) (@mouse-pos 1)))
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
