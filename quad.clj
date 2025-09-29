(import '[org.lwjgl.glfw GLFW]
        '[org.lwjgl.opengl GL GL11])

(GLFW/glfwInit)

(def window-width 1280)
(def window-height 720)

(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow window-width window-height "quad" 0 0))

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
