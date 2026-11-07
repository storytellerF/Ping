# 萍的启动图标

`app-icon.svg` 是可编辑的图标源文件，保留选定设计的叶片、壁纸层叠与镂空波纹。
`app-icon-reference.png` 保存选定的设计参考；实际启动图标使用干净的矢量轮廓和纯色色块。

SVG 使用 108dp 自适应图标坐标，`viewBox="18 18 72 72"` 对应可见区域；
叶片位于中心半径 30dp 内，给圆形裁切和构建 ribbon 留出空间。
背景与前景分离，前景的波纹镂空使用 even-odd 填充。

修改 SVG 的 `background`、`underlayer`、`leaf` 后，从仓库根目录生成 Android 资源：

```sh
python -m pip install -r tools/icons/requirements.txt
python tools/icons/generate.py
```

生成器输出五种密度的普通、圆形 PNG，以及自适应图标的背景、透明矢量前景。
修改轮廓时使用绝对 `M/L/C/Z` 路径。不要手改带有生成标记的 Android XML。
debug/alpha 的 ribbon 仍由 EasyLauncher 在构建时叠加，不画进源文件。

已验证三种构建及 lint；Android 15 真机从 debug、alpha、release APK 分别加载
普通、圆形入口的自适应图标，六项渲染检查均通过，确认叶片颜色及相应 ribbon。
资源重复生成结果一致，五种密度的圆形裁切均保留完整叶片。
