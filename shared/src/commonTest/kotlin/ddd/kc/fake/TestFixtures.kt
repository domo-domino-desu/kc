package ddd.kc.fake

/** 测试资源读取工具。 */
object TestFixtures {
  /**
   * 读取 fixture 目录下的 HTML 文件。
   *
   * @param fileName 文件名（不含 `fixture/` 前缀）。
   */
  fun read(fileName: String): String {
    val path = "fixture/$fileName"
    val stream =
        checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(path)) {
          "Fixture not found: $path"
        }
    return stream.bufferedReader().use { reader -> reader.readText() }
  }
}
