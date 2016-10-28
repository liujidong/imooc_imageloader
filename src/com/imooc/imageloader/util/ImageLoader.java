package com.imooc.imageloader.util;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

/**
 * 图片加载类
 * @author ljd
 *
 */
public class ImageLoader {
	private static ImageLoader mInstance;
	/**
	 * 图片缓存核心对象
	 */
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
	 * 后台轮叙线程
	 */
	private Thread mPoolThread;
	private Handler mPoolThreadHander;
	/**
	 * UI线程Hander
	 */
	private Handler mUIHander;
	public enum Type{
		FIFO,LIFO;
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
						//线程池去取一个任务进行执行
						mThreadPool.execute(getTask());
					}
					
				};
				Looper.loop();
			};
			
		};	
		mPoolThread.start();
		//获取我们应用的最大可用内存
		int maxMemory = (int)Runtime.getRuntime().maxMemory();
		int cacheMemory = maxMemory/8;
		mLruCache = new LruCache<String,Bitmap>(cacheMemory){
			@Override
			protected int sizeOf(String key,Bitmap value){
				return value.getRowBytes()*value.getHeight();
			}
		};
		//创建线程池
		mThreadPool = Executors.newFixedThreadPool(threadCount);
		mTaskQueue = new LinkedList<Runnable>();
		
		mType = type;
	}
	/**
	 * 从任务队列取出
	 * @return
	 */
	private Runnable getTask() {
		if(mType == Type.FIFO){
			return mTaskQueue.removeFirst();
		}else if(mType == Type.LIFO){
			return mTaskQueue.removeLast();
		}
		return null;
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
	public void loadImage(final String path,final ImageView imageView){
		imageView.setTag(path);
		if(mUIHander == null){
			mUIHander = new Handler(){

				@Override
				public void handleMessage(Message msg) {
					//
				}
				
			};
		}
		//根据path在缓存中获取bitmap
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
			addTasks(new Runnable(){

				@Override
				public void run() {
					//加载图片
					//图片的压缩
					//1。获得图片需要显示的大小
					ImageSize imageSize=getImageViewSize(imageView);
					//2.压缩图片
					Bitmap bm = decodeSampledBitmapFromPath(path,imageSize.width,imageSize.height);
				}
				
			});
		}
	}
	/**
	 * 根据ImageView获得适当的压缩宽和高
	 * @param imageView
	 * @return
	 */
	@SuppressLint("NewApi")
	protected ImageSize getImageViewSize(ImageView imageView) {
		ImageSize imageSize = new ImageSize();
		
		DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
		
		LayoutParams lp = imageView.getLayoutParams();
		//int width = (lp.width == LayoutParams.WRAP_CONTENT?0:imageView.getWidth());
		int width = imageView.getWidth();//获得imageView的实际宽度
		if(width <= 0){
			width = lp.width;//获取imageView在layout中声明的宽度
		}
		if(width <= 0){
			width = imageView.getMaxWidth();//检查最大值
		}
		if(width <= 0){
			width = displayMetrics.widthPixels;
		}
		
		int height = imageView.getHeight();//获得imageView的实际宽度
		if(height <= 0){
			height = lp.height;//获取imageView在layout中声明的宽度
		}
		if(height <= 0){
			height = imageView.getMaxHeight();//检查最大值
		}
		if(height <= 0){
			height = displayMetrics.heightPixels;
		}
		
		imageSize.width = width;
		imageSize.height = height;
		
		return imageSize;
	}
	private void addTasks(Runnable runnable) {
		// TODO Auto-generated method stub
		
	}
	/**
	 * 根据图片需要显示的宽和高对图片进行压缩
	 * @param path
	 * @param width
	 * @return
	 */
	protected Bitmap decodeSampledBitmapFromPath(String path,int width,int height){
		//获得图片的宽和高，并不把图片加载到内存中
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds=true;
		//file --> options
		BitmapFactory.decodeFile(path, options);
		
		options.inSampleSize = caculateInSampleSize(options,width,height);
		
		//使用获得的InSampleSize再次解析图片
		options.inJustDecodeBounds=false;
		Bitmap bitmap = BitmapFactory.decodeFile(path, options);
		return bitmap;
	}
	/**
	 * 根据需求的宽和高以及图片的实际宽和高计算SampleSize
	 * @param options
	 * @param width
	 * @param height
	 * @return
	 */
	private int caculateInSampleSize(Options options, int reqWidth, int reqHeight) {
		int width = options.outWidth;
		int height = options.outHeight;
		
		int inSampleSize=1;
		
		if(width > reqWidth || height > reqHeight){
			int widthRadio = Math.round(width*1.0f/reqWidth);
			int heithRadio = Math.round(height*1.0f/reqHeight);
			
			inSampleSize = Math.max(widthRadio, heithRadio);
		}
		return inSampleSize;
	}
	private Bitmap getBitmapFromLruCache(String key) {
		return mLruCache.get(key);
	}
	private class ImageSize{
		int width;
		int height;
	}
	private class ImageBeanHolder{
		Bitmap bitmap;
		ImageView imageView;
		String path;
	}
}
