# BingWallpaper

* API来源(https://blog.csdn.net/theonegis/article/details/45342947)
我们可以通过访问：http://cn.bing.com/HPImageArchive.aspx?format=xml&idx=0&n=1获得一个XML文件，里面包含了图片的地址。

    上面访问参数的含义分别是：<br/> 
    1. format，非必要。返回结果的格式，不存在或者等于xml时，输出为xml格式，等于js时，输出json格式。

    2. idx，非必要。不存在或者等于0时，输出当天的图片，-1为已经预备用于明天显示的信息，1则为昨天的图片，idx最多获取到前16天的图片信息。

    3. n，必要参数。这是输出信息的数量。比如n=1，即为1条，以此类推，至多输出8条。

    4. 在返回的XML文件中我们通过访问images->image->url获得图片地址，然后通过http://s.cn.bing.net/获得的图片地址进行访问。

* jar中-startTime参数对应URL中idx参数 数值为[-1,0,1]<br/>
    -count对应URL中n参数
    
    
**注:运行需要java环境,请自行下载**
