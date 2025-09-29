(import '[org.lwjgl.glfw GLFW]
        '[org.lwjgl.opengl GL GL11 GL15 GL20 GL30]
        '[org.lwjgl BufferUtils])

(GLFW/glfwInit)

(def window-width 1280)
(def window-height 720)

(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow window-width window-height "quad" 0 0))

(GLFW/glfwShowWindow window)
(GLFW/glfwMakeContextCurrent window)
(GL/createCapabilities)

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
out vec4 fragColor;

void main()
{
  vec2 uv = gl_FragCoord.xy / iResolution.xy;
  fragColor = vec4(uv, 0, 1);
}")

(while (not (GLFW/glfwWindowShouldClose window))
       (GL11/glClearColor 0.0 1.0 0.0 1.0)
       (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
       (GLFW/glfwSwapBuffers window)
       (GLFW/glfwPollEvents))

(GLFW/glfwDestroyWindow window)
(GLFW/glfwInit)
(GLFW/glfwTerminate)
