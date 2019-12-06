/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xforms

import org.orbeon.oxf.util.FutureUtils
import org.orbeon.xforms.facade.AjaxServer
import org.scalajs.dom
import org.scalajs.dom.experimental._
import org.scalajs.dom.experimental.domparser.{DOMParser, SupportedType}
import org.scalajs.dom.{FormData, html}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.|
import scala.util.control.NonFatal


object Support {

  def formElemOrDefaultForm(formElem: js.UndefOr[html.Form]): html.Form =
    formElem getOrElse getFirstForm

  def getFirstForm: html.Form =
    $(dom.document.forms).filter(".xforms-form")(0).asInstanceOf[html.Form]

  def adjustIdNamespace(
    formElem : js.UndefOr[html.Form],
    targetId : String
  ): (html.Element, String) = {

    val form   = Support.formElemOrDefaultForm(formElem)
    val formId = form.id

    // See comment on `namespaceIdIfNeeded`
    form → Page.namespaceIdIfNeeded(formId, targetId)
  }

  def stringToDom(xmlString: String): dom.Node =
    try {
      (new DOMParser).parseFromString(xmlString, SupportedType.`application/xml`)
    } catch {
      case NonFatal(_) ⇒
        // If `xmlString` can't be parsed, `parseFromString()` is expected to return an error document, but some
        // browsers (at least IE11) throws an exception instead, so here we catch it to return an error document instead.
        dom.document.createElement("parsererror")
    }

  def fetchText(
    url         : String,
    requestBody : String | FormData,
    contentType : Option[String],
    formId      : String,
    signal      : Option[AbortSignal]
  ): Future[(Int, String, dom.Node)] = {

    val f =
      FutureUtils.withFutureSideEffects(
        before = Globals.requestInProgress = true,
        after  = Globals.requestInProgress = false
      ) {

        val fetchPromise =
          Fetch.fetch(
            url,
            new RequestInit {
              var method         : js.UndefOr[HttpMethod]         = HttpMethod.POST
              var body           : js.UndefOr[BodyInit]           = requestBody
              var headers        : js.UndefOr[HeadersInit]        = contentType map (ct ⇒ js.defined(js.Dictionary("Content-Type" → ct))) getOrElse js.undefined
              var referrer       : js.UndefOr[String]             = js.undefined
              var referrerPolicy : js.UndefOr[ReferrerPolicy]     = js.undefined
              var mode           : js.UndefOr[RequestMode]        = js.undefined
              var credentials    : js.UndefOr[RequestCredentials] = js.undefined
              var cache          : js.UndefOr[RequestCache]       = js.undefined
              var redirect       : js.UndefOr[RequestRedirect]    = RequestRedirect.follow // only one supported with the polyfill
              var integrity      : js.UndefOr[String]             = js.undefined
              var keepalive      : js.UndefOr[Boolean]            = js.undefined
              var signal         : js.UndefOr[AbortSignal]        = signal map (js.defined.apply) getOrElse js.undefined
              var window         : js.UndefOr[Null]               = null
            }
          )

        for {
          response ← fetchPromise.toFuture
          text     ← response.text().toFuture
        } yield
          (
            response.status,
            text,
            Support.stringToDom(text)
          )
      }

    // Side-effect to tell the user in case or error
    f.failed.foreach(AjaxServer.logAndShowError(_, formId))

    f
  }
}
