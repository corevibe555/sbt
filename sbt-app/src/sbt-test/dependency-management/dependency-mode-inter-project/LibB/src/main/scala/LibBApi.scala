package libb

import liba.LibAApi

class LibBApi {
  val a = new LibAApi()
  // Only uses LibA's API, does not directly use CoreApi
  def libbMethod: String = a.libaMethod
}
