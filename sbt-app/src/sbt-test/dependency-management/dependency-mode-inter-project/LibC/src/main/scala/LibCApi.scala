package libc

import libb.LibBApi

class LibCApi {
  val b = new LibBApi()
  def libcMethod: String = b.libbMethod
}
