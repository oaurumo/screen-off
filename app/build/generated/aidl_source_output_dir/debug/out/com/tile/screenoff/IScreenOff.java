/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.tile.screenoff;
public interface IScreenOff extends android.os.IInterface
{
  /** Default implementation for IScreenOff. */
  public static class Default implements com.tile.screenoff.IScreenOff
  {
    @Override public void setPowerMode(boolean turnOff) throws android.os.RemoteException
    {
    }
    @Override public void updateNowScreenState(boolean isScreenOn) throws android.os.RemoteException
    {
    }
    @Override public int getNowScreenState() throws android.os.RemoteException
    {
      return 0;
    }
    @Override public void closeAndExit() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.tile.screenoff.IScreenOff
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.tile.screenoff.IScreenOff interface,
     * generating a proxy if needed.
     */
    public static com.tile.screenoff.IScreenOff asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.tile.screenoff.IScreenOff))) {
        return ((com.tile.screenoff.IScreenOff)iin);
      }
      return new com.tile.screenoff.IScreenOff.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_setPowerMode:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          this.setPowerMode(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_updateNowScreenState:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          this.updateNowScreenState(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getNowScreenState:
        {
          int _result = this.getNowScreenState();
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_closeAndExit:
        {
          this.closeAndExit();
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.tile.screenoff.IScreenOff
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void setPowerMode(boolean turnOff) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((turnOff)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_setPowerMode, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void updateNowScreenState(boolean isScreenOn) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((isScreenOn)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_updateNowScreenState, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public int getNowScreenState() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getNowScreenState, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void closeAndExit() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_closeAndExit, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_setPowerMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_updateNowScreenState = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_getNowScreenState = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_closeAndExit = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
  }
  public static final java.lang.String DESCRIPTOR = "com.tile.screenoff.IScreenOff";
  public void setPowerMode(boolean turnOff) throws android.os.RemoteException;
  public void updateNowScreenState(boolean isScreenOn) throws android.os.RemoteException;
  public int getNowScreenState() throws android.os.RemoteException;
  public void closeAndExit() throws android.os.RemoteException;
}
