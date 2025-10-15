
[![](https://jitpack.io/v/com.gitee.xlk_gitee/mupdf-library.svg)](https://jitpack.io/#com.gitee.xlk_gitee/mupdf-library)

#### 更新
- 6.0.7
  - `MupdfConfig`添加功能开关相关参数:`wpsOpenEnable`
- 6.0.6
  - `MupdfConfig`添加功能开关相关参数:`annotationEnable`,`signatureEnable`,`captureEnable`
- 6.0.5
  - 添加参数`MupdfConfig.annotationSaveDirPath`，定义批注后保存的目录路径
- 6.0.4
  - 颜色选择器进行修改，保证独立，避免引用方布局文件与库中布局文件同名
- 6.0.3
  - 优化颜色选择器，主要添加颜色透明度的功能

#### 使用

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
	        implementation("com.gitee.xlk_gitee:mupdf-library:6.0.0")
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
            include 'armeabi-v7a'
            universalApk false
        }
    }
    packagingOptions {
        resources.pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
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

![](https://gitee.com/xlk_gitee/mupdf-library/raw/master/screenshot/1.png)



![](https://gitee.com/xlk_gitee/mupdf-library/raw/master/screenshot/2.png)