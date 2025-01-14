package fr.free.nrw.commons.actions

import io.reactivex.Observable
import org.wikipedia.dataclient.Service
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Thanks API.
 * Context:
 * The Commons Android app lets you thank another contributor who has uploaded a great picture.
 * See https://www.mediawiki.org/wiki/Extension:Thanks
 */
interface ThanksInterface {
    @FormUrlEncoded
    @POST(Service.MW_API_PREFIX + "action=thank")
    fun thank(
        @Field("rev") rev: String?,
        @Field("log") log: String?,
        @Field("token") token: String,
        @Field("source") source: String?
    ): Observable<MwThankPostResponse?>
}
