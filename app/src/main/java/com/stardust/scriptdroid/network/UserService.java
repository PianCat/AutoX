package com.stardust.scriptdroid.network;

import com.jakewharton.retrofit2.adapter.rxjava2.HttpException;
import com.stardust.scriptdroid.network.api.UserApi;
import com.stardust.scriptdroid.network.entity.user.User;
import com.stardust.util.Objects;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;

/**
 * Created by Stardust on 2017/9/20.
 */

public class UserService {


    public static class LoginStateChange {
        private final boolean mOnline;

        public LoginStateChange(boolean online) {
            mOnline = online;
        }

        public boolean isOnline() {
            return mOnline;
        }
    }

    private static final UserService sInstance = new UserService();
    private final Retrofit mRetrofit;
    private volatile User mUser;

    UserService() {
        mRetrofit = NodeBB.getInstance().getRetrofit();
    }

    public static UserService getInstance() {
        return sInstance;
    }

    public Observable<ResponseBody> login(String userName, final String password) {
        return NodeBB.getInstance()
                .getConfig()
                .flatMap(config ->
                        mRetrofit.create(UserApi.class)
                                .login(Collections.singletonMap("x-csrf-token", config.getCsrfToken()),
                                        userName, password)
                                .doOnError(error -> {
                                    if (error instanceof HttpException && ((HttpException) error).code() == 403) {
                                        NodeBB.getInstance().invalidateConfig();
                                    }
                                }))
                .doOnComplete(this::refreshOnlineStatus);

    }


    public Observable<ResponseBody> register(String email, String userName, String password) {
        return NodeBB.getInstance()
                .getConfig()
                .flatMap(config -> mRetrofit.create(UserApi.class)
                        .register(Collections.singletonMap("x-csrf-token", config.getCsrfToken()),
                                email, userName, password, password));
    }

    public boolean isOnline() {
        return mUser != null;
    }

    private void setUser(User user) {
        User old = mUser;
        mUser = user;
        if (!Objects.equals(old, mUser)) {
            if (user == null) {
                NodeBB.getInstance().invalidateConfig();
            }
            EventBus.getDefault().post(new LoginStateChange(user != null));
        }
    }

    public Observable<Boolean> refreshOnlineStatus() {
        PublishSubject<Boolean> online = PublishSubject.create();
        mRetrofit.create(UserApi.class)
                .me()
                .subscribeOn(Schedulers.io())
                .subscribe(user -> {
                    setUser(user);
                    online.onNext(true);
                    online.onComplete();
                }, error -> {
                    setUser(null);
                    online.onNext(false);
                    online.onComplete();
                });
        return online;
    }

    public Observable<ResponseBody> logout() {
        return NodeBB.getInstance()
                .getConfig()
                .flatMap(config ->
                        mRetrofit.create(UserApi.class)
                                .logout(Collections.singletonMap("x-csrf-token", config.getCsrfToken()))
                )
                .doOnError(Throwable::printStackTrace)
                .doOnComplete(this::refreshOnlineStatus);
    }

    public Observable<User> me() {
        return NodeBB.getInstance().getRetrofit()
                .create(UserApi.class)
                .me()
                .doOnNext(this::setUser)
                .doOnError(error -> setUser(null));
    }
}