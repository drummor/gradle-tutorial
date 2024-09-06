import java.text.SimpleDateFormat
import java.util.*

subprojects {
    apply {
        from ("xxx.gradle.kts")
    }
}
apply {
    from("../scripts/build.gradle.kts")
}
/**
 * 1.创建并注册一个任务
 */

tasks.register("hello") {
    group = "task basic sample"
    description = "this is the first lovely task for showing case."
    doLast {
        println("hello world ~")
    }
}
/**
 * 2. 创建包含多个动作的任务
 */
tasks.register("multipleTask") {
    group = "task basic sample"
    description = "the task has two actions."

    doLast {
        println("second,show the task description.the task description  is \" $description \"")
    }

    doFirst {
        println("first,show the task name.the task name is $name")
    }
}


/**
 * 3. 任务依赖
 */
tasks.register("taskA") {
    group = "taskDependencies"
    doLast {
        println("taskA")
    }
}
tasks.register("taskB") {
    group = "taskDependencies"
    doLast {
        println("taskB：${this.path}")
        this.taskDependencies
    }
    dependsOn("taskA")
}

/**
 * 4. 任务排序
 */
val taskX by tasks.register("taskX") {
    group = "taskOrdering"
    doLast {
        println("taskX")
    }
}

val taskY by tasks.register("taskY") {
    group = "taskOrdering"
    doLast {
        println("taskY")
    }
    mustRunAfter("taskX")
}


/**
 * 5. 设置尾随任务
 */

tasks.register("finalizerTask1") {
    group = "finalizer task"
    doLast {
        println(this.name + " is executed")
    }
}

tasks.register("finalizerTask2") {
    group = "finalizer task"

    doLast {
        println(this.name + " is executed")
    }
}

tasks.named("taskA") {
    finalizedBy("finalizerTask1")
}
tasks.named("taskB") {
    finalizedBy("finalizerTask2")
}


/**
 * 6. task's input and output
 */

abstract class RevertTextTask @Inject constructor(private val project: Project) : DefaultTask() {
    init {
        group = "input and out sample"
    }

    @InputFile
    lateinit var inputTextFile: File


    @OutputFile
    lateinit var outputTextFile: File


    @TaskAction
    fun revert() {
        val text = inputTextFile.readText()
        val reversedText = text.reversed()
        outputTextFile.writeText(reversedText)
    }
}

tasks.register<RevertTextTask>("revertTextTask") {
    inputTextFile = layout.projectDirectory.file("input.txt").asFile
    val outFile = layout.projectDirectory.file("out.text")
    if (outFile.asFile.exists().not()) {
        outFile.asFile.createNewFile()
    }
    outputTextFile = layout.projectDirectory.file("out.text").asFile
}


/**
 * 7. task's 惰性
 */

abstract class GenerateGreetingTask : DefaultTask() {

    @get:Input
    abstract val greetingText: Property<String>

    @get:OutputFile
    abstract val greetingFile: RegularFileProperty

    init {
        group = "input and out sample"
    }

    @TaskAction
    fun execute() {

        if (greetingFile.get().asFile.exists().not()) {
            greetingFile.get().asFile.createNewFile()
        }
        greetingFile.get().asFile.writeText(greetingText.get())
        println("write greeting text success !")
    }
}

val greetingTask = tasks.register<GenerateGreetingTask>("generateGreetingTask") {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss ")
    val time = dateFormat.format(Date())
    val text = """
            say hello , when $time from China !
        """.trimIndent()
    greetingText.set(text)

    this.greetingFile.set(layout.projectDirectory.file("greeting.txt"))
}


abstract class ReplyTask : DefaultTask() {
    init {
        group = "input and out sample"
    }

    @get:InputFile
    abstract val greetingFile: RegularFileProperty

    @get:OutputFile
    abstract val replyFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val greetingText = greetingFile.get().asFile.readText()
        val replyText = StringBuilder(greetingText).appendLine().append("I fine , thx！")

        if (replyFile.asFile.get().exists().not()) {
            replyFile.asFile.get().createNewFile()
        }

        //ConfigurableFileCollection
        replyFile.asFile.get().writeText(replyText.toString())
    }
}


tasks.register<ReplyTask>("replyTask") {

    greetingFile.set(greetingTask.get().greetingFile)
    replyFile.set(layout.projectDirectory.file("reply.text").asFile)
}


/**
 * 8.增量测试
 * Disabling up-to-date checks
 */
//@UntrackedTask(because = "time should refresh") //通过标注该注解。注明不UP-TO-DATE
abstract class LogTimeTask : DefaultTask() {
    init {
        this.doNotTrackState("Instrumentation needs to re-run every time")
    }

    @get:OutputFile
    abstract val outTimeFile: RegularFileProperty

    @TaskAction
    fun execute() {
        outTimeFile.get().asFile.writeText(Date().time.toString())
    }
}

tasks.register<LogTimeTask>("logTime") {
    outTimeFile.set(layout.projectDirectory.file("log-time.txt").asFile)
}


/**
 * 9. 构建缓存
 */
@CacheableTask
abstract class PrintHelloCoffee : DefaultTask() {
    init {
        group = "build  cache"
    }

    @get:OutputFile
    abstract val coffeeDes: RegularFileProperty


    @TaskAction
    fun execute() {
        coffeeDes.get().asFile.writeText("hello world !")
    }
}

tasks.register<PrintHelloCoffee>("buildCache") {
    coffeeDes.set(layout.buildDirectory.file("coffee.txt"))
}


/**
 * 10. 自定义增量构建
 */
@CacheableTask
abstract class IncrementalTask : DefaultTask() {

    @get:Incremental
    @get:InputDirectory
    // 当normalized path 的时候会取到什么
    // 当
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputDir: DirectoryProperty


    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun execute(inputChange: InputChanges) {
        val msg = if (inputChange.isIncremental) {
            "CHANGED inputs are out of date"
        } else {
            "ALL inputs are out of date"
        }
        println(msg)
        inputChange.getFileChanges(inputDir).forEach { change ->
            // 1. 如果是目录直接返回
            if (change.fileType == FileType.DIRECTORY) {
                println("dir change~")
                return@forEach
            }

            // 2.找到要输出的文件
            val normalized = change.normalizedPath
            val normalizedFile = change.file
            println("normalized $normalized")
            println("changed file $normalizedFile")
            val targetFile = outputDir.file(normalized).get().asFile
            if (targetFile.parentFile.exists().not()) {
                targetFile.parentFile.mkdirs()
            }
            // 3.根据文件的变化处理输出
            when (change.changeType) {

                ChangeType.ADDED -> {
                    println("dir add targetFile ~$targetFile")
                    println("dir add change ~${change.file}")
                    targetFile.writeText(change.file.readText().reversed())
                }

                ChangeType.REMOVED -> {
                    println("dir remove~")
                    targetFile.delete()
                }

                ChangeType.MODIFIED -> {
                    println("dir modified~")
                    targetFile.writeText(change.file.readText().reversed())
                }
            }
        }
    }
}

tasks.register<IncrementalTask>("incrementalTask")
{
    inputDir.set(layout.projectDirectory.dir("incrementalInputDir"))
    outputDir.set(layout.buildDirectory.dir("incrementalOutputDir"))
}


/**
 * 11. skipped
 */
tasks.register("skippedTask") {

    actions.clear()
}


/**
 * 12.NO-SOURCE.
 *
 */
@UntrackedTask(because = "time should refresh")
abstract class NoSourceTask : DefaultTask() {
    // @get:SkipWhenEmpty
    // @get:InputFile
    // abstract val nameFile: RegularFileProperty
    @get:OutputFile
    abstract val outputFileName: RegularFileProperty

    @TaskAction
    fun execute() {
        println("noSource execute~")
        didWork = true
    }
}
tasks.register<NoSourceTask>("noSource") {

    outputFileName.set(layout.projectDirectory.file("x"))
}