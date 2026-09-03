
[![](https://jitpack.io/v/com.github.kelvin-panda/mupdf.svg)](https://jitpack.io/#com.github.kelvin-panda/mupdf)
# 更新
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
	        implementation("com.github.kelvin-panda:mupdf:6.0.35")
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
