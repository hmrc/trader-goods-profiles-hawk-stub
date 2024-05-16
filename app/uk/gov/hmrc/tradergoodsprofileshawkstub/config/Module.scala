package uk.gov.hmrc.tradergoodsprofileshawkstub.config

import com.google.inject.AbstractModule

class Module extends AbstractModule {

  override def configure(): Unit = {

    bind(classOf[AppConfig]).asEagerSingleton()
  }
}
