
[![](https://jitpack.io/v/com.gitee.xlk_gitee/mupdf-library.svg)](https://jitpack.io/#com.gitee.xlk_gitee/mupdf-library)


**步骤1**
Add it in your settings.gradle.kts at the end of repositories:

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
	        implementation("com.github.cdck:mupdf-library:1.0")
	}
```
**步骤3**
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



![](https://gitee.com/xlk_gitee/mupdf-library/raw/master/screenshot/1.png)



![](https://gitee.com/xlk_gitee/mupdf-library/raw/master/screenshot/2.png)