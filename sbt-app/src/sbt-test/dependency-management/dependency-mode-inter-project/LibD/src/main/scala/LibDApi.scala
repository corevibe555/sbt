package libd

import libc.LibCApi

class LibDApi {
  val c = new LibCApi()
  def libdMethod: String = c.libcMethod
}
