记录`MuPDF`的使用步骤



- 进入页面先获取首页宽高，再根据屏幕宽高计算全屏需要的缩放比例，传递给`ReaderView`，使后续页面内容全屏展示



`MuPDF`的内部流程

- 使用自定义`ReaderView extends AdapterView`展示`pdf`文件的内容
- `PageAdapter`中的`Item`布局为自定义的`PageView`
  - 1.先获取页面的大小
  - 2.获取成功后通过`pageView.setPage`方法进行渲染
- `PageView`通过父控件和`MupdfMacro.clarityLimitMode`确定宽高
- `PageView`的关键方法`setPage`中，通过`getDrawPageTask`渲染`mEntire`展示当前页的内容
- 通过`MuPDFCore`中的`drawPage`方法传递`BitMap`进行`native`内部加载

加载优化
> 7张图片内部打印就加载了7秒

- 避免多次调用c层渲染，有些操作可以在前端实现
- 缓存优化，存取旧的bitmap使用，避免手指随意触碰就刷新
- 清晰度进行限制

`PageView详解`
- `ImageView`对象`mEntire`:停下来查看时`mEntire`提供高清渲染。渲染整页
  - 用途场景：静止、高质量查看。当页面以100%缩放比例（或接近）静止显示时使用。
  - 渲染速度：慢。首次生成耗时，但生成后可复用。
- `ImageView`对象`mPatch`:用户在快速滑动时依赖`mPatch`保证流畅。渲染部分
  - 用途场景：动态交互。在缩放动画过程和快速滑动过程中使用。
  - 渲染速度：快。只渲染一小块区域，满足实时交互需求。
  - 流程：
    - 每次拖动、放大、缩小操作结束时会通过`updateHq`进行加载。
    - 加载结束时调用`mPatch.layout(mPatchArea.left, mPatchArea.top, mPatchArea.right, mPatchArea.bottom);`
    - 这时候`PageView`的`onLayout(boolean changed, int left, int top, int right, int bottom)`方法响应
    - 方法中判断宽高与`mEntire`的不一致，则对`mEntire`进行相应的缩放