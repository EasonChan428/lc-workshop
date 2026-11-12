import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class FileWalker {
  public static void main(String[] args) throws IOException {
    
    // 1. 要扫描的目录
    Path root = Path.of("C:\\Users\\eason\\Desktop\\MyCode\\git\\src\\leetcode");
    
    // 2. 用一个List装结果，先声明在外面（后面会用到）
    List<Path> javaFiles;

    // 3. 遍历目录，筛出所有 .java 文件
    //    try (...) { } 这种写法叫 try-with-resources：
    //    花括号结束时自动关闭 stream，不用手动 close
    try(Stream<Path> stream = Files.walk(root)) {
      javaFiles = stream
              .filter(p -> p.toString().endsWith(".java")) // 只要 .java 结尾的
              .filter(Files::isRegularFile)                // 只要文件，不要文件夹
              .sorted()                                    // 按路径排序，输出稳定
              .toList();                                   // 收集成 List

    }

    // 4. 打印每个文件名
    for(Path p : javaFiles) {
      System.out.println(p.getFileName());
    }

    // 5. 打印总数
    System.out.println("一共" + javaFiles.size() + "个文件");
  }
}