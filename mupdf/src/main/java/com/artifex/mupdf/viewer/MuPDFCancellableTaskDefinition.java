package com.artifex.mupdf.viewer;

import com.artifex.mupdf.fitz.Cookie;

public abstract class MuPDFCancellableTaskDefinition<Params, Result> implements CancellableTaskDefinition<Params, Result>
{
	private Cookie cookie;

	public MuPDFCancellableTaskDefinition()
	{
		this.cookie = new Cookie();
	}

	@Override
	public void doCancel()
	{
		// 不调用 cookie.abort()——中途取消 native 渲染会导致设备栈残留
		// (items left on stack)，进而引发 SIGSEGV。
		// 由 CancellableAsyncTask.cancel() 中的 asyncTask.get() 等待任务自然完成。
	}

	@Override
	public void doCleanup()
	{
		if (cookie == null)
			return;

		cookie.destroy();
		cookie = null;
	}

	@Override
	public final Result doInBackground(Params ... params)
	{
		return doInBackground(cookie, params);
	}

	public abstract Result doInBackground(Cookie cookie, Params ... params);
}
