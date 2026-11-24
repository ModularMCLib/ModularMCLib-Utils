# ModularMCLib Utils

面向 Minecraft 26.1.2、NeoForge 26.1.2.126 与 Java 25 的通用工具库。模组标识为 `mmclutils`。

## 安装与构建

将完整 JAR 放入客户端或专用服的 `mods` 目录。完整 JAR 内嵌 Kotlin 标准库。自行管理运行依赖的环境可以使用 slim JAR，由宿主提供同版本的 Kotlin 标准库。

在仓库根目录使用 Gradle Wrapper 构建。产物位于 `build/libs/`，包含完整 JAR、slim JAR 和源码 JAR。

```powershell
.\gradlew.bat check clientClasses jar slimJar sourcesJar
```

## 公共入口

| 功能 | 入口 | 用途 |
| --- | --- | --- |
| 集合与缓存 | `collection.cache`、`collection.map`、`collection.map.hash` | 强值和弱值缓存、驻留、枚举映射、计数器及按自定义哈希策略查找 |
| 调度与并发 | `Schedulers`、`Scope`、`AsyncLane`、`SerialTaskExecutor`、`BoundedVirtualTaskQueue` | Tick 调度、生命周期清理、串行写入和有界后台计算 |
| 区块与空间 | `LoadedChunks`、`BlockPositions`、`BlockEntityIndex`、`VoxelShapes` | 已加载区块查询、打包坐标、方块实体索引和体素采集 |
| 文件与存档 | `FileUtils`、`BinaryCodecs`、`FastSavedData` | 文件与流读写、内容摘要、按路径互斥、原子替换、二进制编解码和维度存档 |
| 库存与方块能力 | `InventoryTransfers`、`FluidTransfers`、`FluidContainers`、`AdjacentBlockCapabilityCache` | 物品与流体事务、流体容器装卸和邻接能力缓存 |
| 按键与同步 | `SyncedKeyMappings`、`ClientKeyBindings`、`KeyChordMapping` | 两端共用按键标识、客户端组合键与服务端玩家状态 |
| 文本与基础计算 | `Components`、`TextFormats`、`ColorMath`、`NumberMath`、`ResourcePacks` | 原版组件、颜色和数值计算、资源包操作 |
| 客户端渲染 | `EffectRenderer`、`ParticleEffects`、`BeamEffects`、`SphereHighlight` | 环、粒子、连线、玩家附着和定时高亮 |
| 客户端模型与界面 | `ClientModels`、`QuadView`、`GuiDrawing`、`ImageTextures` | 模型组合、四边形读写、界面绘制和动态贴图 |

`ModularMCLibUtils` 提供服务器访问、资源标识和运行环境查询。`ModularMCLibUtilsClient` 提供客户端玩家、维度和线程查询。NeoForge 向两个入口注入各自使用的 `IEventBus`。

### 调度与数据

在所属维度的 Tick 结束时推进任务。以下示例将工作延后 20 个 Tick，并把可取消的任务交给调用方。

```java
import com.modularmc.mmclutils.schedule.Schedulers;
import com.modularmc.mmclutils.schedule.Task;

import net.minecraft.server.level.ServerLevel;

public class WorldTasks {

    public static Task runLater(ServerLevel level, Runnable action) {
        return Schedulers.of(level).schedule(20, action);
    }
}
```

`Schedulers.async(level)` 可安排后台计算。世界数据在所属线程采集，后台读取独立数据，结果交回所属线程后再修改世界。

普通 Codec 存档使用原版 `SavedDataStorage`。使用 `FastSavedData` 时，由调用方长期持有 `Type`，在服务器线程修改 `value()` 后调用 `setDirty()`。类型提供独立写盘数据的构造方式及可选的旧格式读取器。保存命令的 `flush`、维度卸载和停服会等待已提交的写入。

## 许可

项目元数据声明 GNU AGPL 3.0。第三方声明随完整 JAR 和 slim JAR 分发。 [分发声明](src/main/resources/META-INF/NOTICE.md)
