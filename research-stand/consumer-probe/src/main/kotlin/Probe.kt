import dev.youndie.proba.sample.lib.Gate

// What a consumer writes. Nothing exotic: it calls the one public function the library offers.
val issued = Gate.issue("hello")
