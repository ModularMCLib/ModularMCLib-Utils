# ModularMCLib Utils

面向 Minecraft 26.1.2、NeoForge 26.1.2.126 与 Java 25 的通用工具库。模组标识为 `mmclutils`。

## 安装与构建

将完整 JAR 放入客户端或专用服的 `mods` 目录。完整 JAR 内嵌 Kotlin 标准库。自行管理运行依赖的环境可以使用 slim JAR，由宿主提供同版本的 Kotlin 标准库。

在仓库根目录使用 Gradle Wrapper 构建。产物位于 `build/libs/`，包含完整 JAR、slim JAR 和源码 JAR。

```powershell
.\gradlew.bat check clientClasses jar slimJar sourcesJar
```

## 公共入口

以下包均位于 `com.modularmc.mmclutils`。

| 包 | 入口 | 用途 |
| --- | --- | --- |
| `utils` | `FileUtils`、`ResourceUtils`、`RegistriesUtils`、`TagUtils` | 文件和流读写、原子替换、摘要、资源包定位、注册表和标签 |
| `utils` | `ArrayUtils`、`MapUtils`、`ColorUtils`、`MathUtils`、`NumberUtils`、`StringUtils`、`ComponentUtils` | 数组和映射、颜色、数值计算与格式化、字符串搜索和原版文本组件 |
| `utils` | `BlockPosUtils`、`DirectionUtils`、`EntityUtils` | 坐标排序、打包位置、方块朝向和实体查询 |
| `utils` | `ItemUtils`、`FluidUtils`、`InventoryUtils`、`FluidContainerUtils` | 栈比较、分栈和计数、库存事务、流体容器装卸 |
| `api.collection` | `cache`、`map`、`map.hash` | 缓存、驻留、枚举容器、计数器和自定义哈希集合 |
| `api.schedule` | `Schedulers`、`Scope`、`AsyncLane`、`SerialTaskExecutor`、`BoundedVirtualTaskQueue` | Tick 调度、所有者清理、串行任务和有界后台计算 |
| `api.level`、`api.blockentity`、`api.shape` | `LoadedChunks`、`LevelStore`、`BlockEntityIndex`、`VoxelShapeCache` | 已加载区块、维度状态、方块实体索引和体素形状采集 |
| `api.data` | `BinaryCodecs`、`CodecUtils`、`FastSavedData` | 二进制读写、原版 Codec 和按维度保存数据 |
| `api.capability`、`api.inventory`、`api.recipe` | `AdjacentBlockCapabilityCache`、`ResourceTransferSimulation`、`IngredientMatching` | 邻接能力缓存、事务仿真和配料匹配 |
| `api.input`、`client.input` | `SyncedKeyMappings`、`ClientKeyBindings`、`KeyChordMapping` | 两端共用的按键标识、组合键和服务端玩家状态 |
| `client.model`、`client.gui`、`client.texture` | `ModelUtils`、`QuadView`、`QuadUtils`、`GuiUtils`、`ImageTextures` | 模型组合、四边形读写与 UV、界面绘制和动态贴图 |
| `client.render.effect`、`client.render.geometry` | `EffectRenderer`、`ParticleEffects`、`BeamEffects`、`SectionGeometryRenderer` | 环、粒子、连线、定时高亮和区段几何绘制 |

`ModularMCLibUtils` 提供服务器访问、资源标识和运行环境查询。`ModularMCLibUtilsClient` 提供客户端玩家、维度和线程查询。NeoForge 向两个入口注入各自使用的 `IEventBus`。

### 调度与数据

在所属维度的 Tick 结束时推进任务。以下示例将工作延后 20 个 Tick，并把可取消的任务交给调用方。

```java
import com.modularmc.mmclutils.api.schedule.Schedulers;
import com.modularmc.mmclutils.api.schedule.Task;

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
