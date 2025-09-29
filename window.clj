(import '[javax.imageio ImageIO]
        '[org.lwjgl BufferUtils]
        '[org.lwjgl.glfw GLFW GLFWCursorPosCallbackI GLFWMouseButtonCallbackI]
        '[org.lwjgl.opengl GL GL11 GL13 GL15 GL20 GL30]
        '[org.lwjgl.stb STBImageWrite])

(GLFW/glfwInit)

(def window-width 1280)
(def window-height 720)

(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow window-width window-height "example" 0 0))

(GLFW/glfwShowWindow window)
(GLFW/glfwMakeContextCurrent window)
(GL/createCapabilities)

(while (not (GLFW/glfwWindowShouldClose window))
       (GL11/glClearColor 0.0 1.0 0.0 1.0)
       (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
       (GLFW/glfwSwapBuffers window)
       (GLFW/glfwPollEvents))

(GLFW/glfwDestroyWindow window)
(GLFW/glfwInit)
(GLFW/glfwTerminate)
