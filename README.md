#这个是一个 LocalBroadcastManager 的补充版
 
开发中遇到一个问题，当应用处于前台的时候，我们需要在某些情况下做一些 UI 提示，然后立即处理。
当我们的应用不在前台的时候，暂不做任何 UI 上的处理，当应用回到前台的时候立即做 UI 提示。

所以我们碰到了一个问题:如何去判断我的应用是否处于前台？

一开始的方案是参考应用锁，获取系统当前栈，根据栈的信息来判断我们的应用是否处于前台。
后来我们发现这个方法并不是很好，一个是针对不同的 Android 版本要做不同的逻辑处理，
还有就是可能到后面都不一定有权限取到系统的当前栈。

我们后来又想尝试在需要修改 UI 的时候，通过 Runtime 执行 `dumpsys activity top` 的命令
来获取系统当前的 Activity ，但是我们发现，这个其实并不是很好，因为每个系统返回的命令的格式
并不一定相同，还是要做很多的适配处理。

后来我又想到另外一种方法。
每个 Activity `onStart` 的时候注册一个广播，然后在 `onStop` 的时候注销这个广播。然后在需要
做 UI 提示的时候发送一个广播。
首先，当我们的 app 是前台的时候，必定有一个 Activity 调用了 `onStart` 而没有调用 `onStop`
如果这个时候有个 UI 提示的广播，可以立即处理。
当我们的 app 不是前台的时候，我们 app 的每一个 Activity 一定先后调用了 `onStart` 和 `onStop`
此时，如果 app 发送了一个 UI 提示的广播，由于没有接收器，这个广播就不被处理了。到这里貌似都符合
需求，但是后台转到前台的时候，貌似不会做 UI 提示，应为那个 UI 提示的消息被`消费`了。
后来我又想到有一个 Sticky 的广播，发送的 Sticky 类型的广播会一直存在，后续如果有广播注册，新的
广播会接收到这个 Sticky 广播。当我们处理完后 remove 这个 Sticky 的广播。

我们为了保证消息的下发效率和保护自己的消息，我们想用 `LocalBroadcastManager` 来发送广播，但是我们
发现 `LocalBroadcastManager` 并没有 Sticky 类型的，于是我做了修改，且 Sticky 广播被处理后会自动被 remove

# 如何使用

在你的工程根目录下的 `build.gradle` 文件添加如下内容
```
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

在你的 apk 工程目录下的`build.gradle` 添加
```
	dependencies {
		compile 'com.github.XanderWang:LocalStickyBroadcast:0.0.1'
	}
```