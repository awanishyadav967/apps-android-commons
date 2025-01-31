package fr.free.nrw.commons;

import androidx.annotation.NonNull;
import okhttp3.OkHttpClient;
import org.wikipedia.AppAdapter;
import org.wikipedia.dataclient.Service;
import org.wikipedia.dataclient.SharedPreferenceCookieManager;
import org.wikipedia.dataclient.WikiSite;
import org.wikipedia.dataclient.okhttp.TestStubInterceptor;
import org.wikipedia.dataclient.okhttp.UnsuccessfulResponseInterceptor;

public class TestAppAdapter extends AppAdapter {

    @Override
    public String getMediaWikiBaseUrl() {
        return Service.WIKIPEDIA_URL;
    }

    @Override
    public String getRestbaseUriFormat() {
        return "%1$s://%2$s/api/rest_v1/";
    }

    @Override
    public OkHttpClient getOkHttpClient(@NonNull WikiSite wikiSite) {
        return new OkHttpClient.Builder()
            .addInterceptor(new UnsuccessfulResponseInterceptor())
            .addInterceptor(new TestStubInterceptor())
            .build();
    }

    @Override
    public int getDesiredLeadImageDp() {
        return 0;
    }

    @Override
    public boolean isLoggedIn() {
        return false;
    }

    @Override
    public String getUserName() {
        return null;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public SharedPreferenceCookieManager getCookies() {
        return null;
    }

    @Override
    public void setCookies(@NonNull SharedPreferenceCookieManager cookies) {
    }

    @Override
    public boolean logErrorsInsteadOfCrashing() {
        return false;
    }
}

