package bferrari.com.mlkitdemo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.ActivityOptionsCompat
import android.widget.Toast

inline fun <reified T : Activity> Activity.navigate(
        bundle: Bundle? = null,
        options: ActivityOptionsCompat? = null) {
            val intent = Intent(this, T::class.java)
            intent.apply {
                bundle?.let {
                    putExtras(bundle)
                }
                startActivity(this, options?.toBundle())
            }
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}