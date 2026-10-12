
[![](https://jitpack.io/v/com.github.kelvin-panda/mupdf.svg)](https://jitpack.io/#com.github.kelvin-panda/mupdf)
# 更新
## 6.0.43（未发布）
1. 批注提交改为**局部 patch 重绘**：只重绘笔迹包围盒（实测 3–8ms，原整页重绘约 160ms），
   页面内容区与整页重绘逐像素一致；撤销/共享等其它刷新路径仍走整页重绘
2. 库版本号改为单一来源：由 `mupdf/build.gradle` 的 `jitpackVersion`（JitPack `VERSION` 环境变量 / 本地兜底值）注入为
   `R.string.mupdf_library_version`，启动日志不再硬编码
## 6.0.42
1. 修复批注绘制出现"两条线"：即时保存后同一笔不再同时被浮层和页面各渲染一份，只保留实时预览并在页面渲染完成后撤销
   - 浮层已提交笔画按滚动差平移，随文档（含双指平移/页面重排）一起移动，不再与 PDF 批注错开
   - 落笔、双指接管、进入批注三个时机停止惯性/翻页滚动动画，避免批注期间文档继续滑动导致坐标基准漂移
2. 修复绘制后闪屏：预览线宽改为按页面比例换算成与 PDF 批注相同的屏幕宽度，交接不再粗细跳变
## 6.0.41
1. 批注改为**即时保存**：每笔松开即写入 PDF 标注对象
2. 撤销/擦除/共享统一重构为基于 `operationId` 的操作流
   - 新增 `MupdfOperationId`，`MupdfInkBean` 支持 `OP_ADD / OP_ERASE / OP_UNDO` 与百分比坐标分段
   - 移除 `MupdfAnnotationBean`，`MuPDFCore` 增加按 operationId 注册/删除批注的能力
## 6.0.40
1. 适配预览中实时切换新的PDF文件
2. 新增`shouldSignature`配置参数：`=true`时PDF文件打开后展示签名框进行PDF的文件签名
3. 优化`ArtBoardDialog`，当`cancelable`为`false`时隐藏掉取消按钮
## 6.0.39
1. 修改签名Bitmap的获取方式
## 6.0.37
1. example 改为使用本地 `project(":mupdf")` 依赖，避免 JitPack 构建时引用尚未发布的远程版本
## 6.0.36
1. 添加签名提交的功能（测试）
## 6.0.35
1. 发布源迁移到 GitHub，坐标改为 `com.github.kelvin-panda:mupdf`
## 6.0.28
1. Activity添加`dispatchTouchEvent`拦截`MotionEvent.ACTION_DOWN`事件，通知给前端使用
2. `MupdfConfig`添加`informSignature`开关，用于秘书端通知参会端签名（未完善）
## 6.0.27
1. 界面右下角添加固定的返回控件
   - 可在`MupdfConfig`配置`backButtonEnabled`，默认`false`隐藏
## 6.0.26
1. 修复下划线删除线无法被擦除的问题
2. 修复多批注同一位置擦除崩溃问题
3. 改用`mWindowWatermarkEnabled`控制水印的显示与隐藏
## 6.0.25
1. 添加外部可控显示或隐藏的功能开关
   - 顶部菜单可显示或隐藏：水印、签名表、填写签名、设置里的水印（待完善使用场景）
   - 批注中的文本功能：退出批注时无法撤销和删除（待完善）
2. UI更新
   - 恒迅版本的缩略图图标
   - 退出时的保存提示：字体增大与调整间距
## 6.0.24
1. 适配再上线上传功能批注保存时文件名去除日期，交给外部处理
   - MuPDFCore 中 save 方法文件名去除日期与批注
2. 去除无效控件
   - 存在退出时批注保存的提示，`viewTopSave`控件无用（预留未删除）
3. 水印功能完全手动
   - 传递过来的水印内容需要用户手动设置
## 6.0.18
  - 依赖库`AndroidAutoSize`，让`MuPdfDocumentActivity`实现`CancelAdapt`。临时更新解决调用方使用此库导致的界面UI错乱的问题
## 6.0.17
  - 限制频繁点击刷新
## 6.0.16
  - 定制版本外部打开图标改成wps
  - 魔法值修复
## 6.0.15
  - 调整画笔工具关闭的图标位置到最后
## 6.0.14
  - 美化和修改图标，添加英文环境
## 6.0.13
  - 批注和签名后恢复原位置,添加参数`isSimulating`拦截响应`onFling`事件，避免模拟拖动导致的甩动效果
## 6.0.12
  - 批注和签名后恢复原位置
## 6.0.11
  - 签名添加画笔颜色自选功能
  - 打开后延迟加载一次界面，预防首次加载时模糊的情况
## 6.0.10
  - 添加功能参数`MupdfConfig.clarityLimitMode`用于限制清晰度以提高加载速度，默认不限制
  - 添加功能参数`MupdfConfig.fullScreenEnable`用于决定是否全屏缩放，默认全屏
## 6.0.9
  - 添加了`arm64-v8a`架构的so库
## 6.0.8
  - 批注后的文件名只添加年月日的标识，去掉后面的时分秒
  - 添加文件名递增相关方法
## 6.0.7
  - `MupdfConfig`添加功能开关相关参数:`wpsOpenEnable`
## 6.0.6
  - `MupdfConfig`添加功能开关相关参数:`annotationEnable`,`signatureEnable`,`captureEnable`
## 6.0.5
  - 添加参数`MupdfConfig.annotationSaveDirPath`，定义批注后保存的目录路径
## 6.0.4
  - 颜色选择器进行修改，保证独立，避免引用方布局文件与库中布局文件同名
## 6.0.3
  - 优化颜色选择器，主要添加颜色透明度的功能

# 使用

**步骤1**
Add it in your `settings.gradle.kts` at the end of repositories:

```
	dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url = uri("https://jitpack.io") }
		}
	}
```
**步骤2**

```
	dependencies {
	        implementation("com.github.kelvin-panda:mupdf:6.0.42")
	}
```
**依赖方module**
```
android{
    //... ...
    
    //解决API 28只创建arm64目录，导致找不到库的问题
    splits {
        abi {
            enable true
            reset()
            include 'armeabi-v7a','arm64-v8a'
            universalApk false
        }
    }
    packagingOptions {
        resources.pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
        resources.pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
    }
}
```

**使用**

```java
    private void openMupdf(Uri uri) {
        MupdfConfig mupdfConfig = new MupdfConfig.Builder()
                .fileUri(uri.toString())//文件uri
                .build();
        MuPdfDocumentActivity.jump(this, mupdfConfig);
    }
```

**示例图**

![](https://raw.githubusercontent.com/kelvin-panda/mupdf/master/screenshot/1.png)



![](https://raw.githubusercontent.com/kelvin-panda/mupdf/master/screenshot/2.png)
