package com.imooc.imageloader.util;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.ImageView;

/**
 * 图片加载类
 * @author ljd
 *
 */
public class ImageLoader {
	private static ImageLoader mInstance;
	private LruCache<String,Bitmap> mLruCache;
	/*
	 * 线程池
	 */
	private ExecutorService mThreadPool;
	private static final int DEAFULT_THREAD_COUNT = 1;
	/**
	 * 队列的调度方式
	 */
	private Type mType = Type.LIFO;
	/**
	 * 任务队列
	 */
	private LinkedList<Runnable> mTaskQueue;
	/**
	 * 后台轮询线程
	 */
	private Thread mPoolThread;
	private Handler mPoolThreadHander;
	/**
	 * UI线程的Hander
	 */
	private Handler mUIHander;
	public enum Type{
		FIFI,LIFO;
	}
	
	private ImageLoader(int threadCount,Type type){
		init(threadCount,type);
	}
	/**
	 * chushihua
	 * @param threadCount
	 * @param type
	 */
	private void init(int threadCount, Type type) {
		mPoolThread = new Thread(){

			@Override
			public void run() {
				Looper.prepare();
				mPoolThreadHander = new Handler(){

					@Override
					public void handleMessage(Message msg) {
						//
					}
					
				};
				Looper.loop();
			};
			
		};	
		mPoolThread.start();
		//h
		int maxMemory = (int)Runtime.getRuntime().maxMemory();
		int cacheMemory = maxMemory/8;
		mLruCache = new LruCache<String,Bitmap>(cacheMemory){
			protected int sizeOf(String key,Bitmap value){
				@Override
				return value.getRowBytes()*value.getHeight();
			}
		};
		mThreadPool = Executors.newFixedThreadPool(threadCount);
		mTaskQueue = new LinkedList<Runnable>();
		
		mType = type;
	}
	public static ImageLoader getInstance(){
		if(mInstance == null){
			synchronized (ImageLoader.class) {
				if(mInstance == null){
					mInstance = new ImageLoader(DEAFULT_THREAD_COUNT,Type.LIFO);
				}
			}
		}
		return mInstance;
	}
	public void loadImage(String path,ImageView imageView){
		imageView.setTag(path);
		if(mUIHander == null){
			mUIHander = new Handler(){

				@Override
				public void handleMessage(Message msg) {
					//
				}
				
			};
		}
		Bitmap bm = getBitmapFromLruCache(path);
		if(bm != null){
			Message message = Message.obtain();
			ImageBeanHolder holder = new ImageBeanHolder();
			holder.bitmap = bm;
			holder.path = path;
			
			if(imageView.getTag().toString().equals(path)){
				imageView.setImageBitmap(bm);
			}
			holder.imageView = imageView;
			message.obj = holder;
			mUIHander.sendMessage(message);
		}else{
			
		}
	}
	private Bitmap getBitmapFromLruCache(String key) {
		return mLruCache.get(key);
	}
	private class ImageBeanHolder{
		Bitmap bitmap;
		ImageView imageView;
		String path;
	}
}
