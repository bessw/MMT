package info.kwarc.mmt.mathhub.library

import info.kwarc.mmt.api.utils.{JSONArray, MMTSystem}
import info.kwarc.mmt.api.web.{ServerRequest, ServerResponse}
import info.kwarc.mmt.mathhub.{PathNotFound, Server}

trait ContentServer { this: Server =>
  protected def applyContent(contentPath: List[String], request: ServerRequest) : ServerResponse = contentPath match {
    case "version" :: Nil =>
      toResponse(getMMTVersion)

    case "uri" :: Nil =>
      toResponse(getURI(request.parsedQuery.string("uri", return missingParameter("uri"))))
    case "groups" :: Nil =>
      toResponse(getGroups())
    case "group" :: Nil =>
      toResponse(getGroup(request.parsedQuery.string("id", return missingParameter("id"))))
    case "archive" :: Nil =>
      toResponse(getArchive(request.parsedQuery.string("id", return missingParameter("id"))))
    case "document" :: Nil =>
      toResponse(getDocument(request.parsedQuery.string("id", return missingParameter("id"))))
    case "module" :: Nil =>
      toResponse(getModule(request.parsedQuery.string("id", return missingParameter("id"))))
    case _ => throw PathNotFound(request)
  }

  /** turns an object into a server response */
  private def toResponse(result: Option[IResponse]): ServerResponse = result match {
    case Some(r) => ServerResponse.fromJSON(r.toJSON)
    case None => ServerResponse("Not found", "text", ServerResponse.statusCodeNotFound)
  }
  private def toResponse(results: List[IResponse]): ServerResponse = {
    ServerResponse.JsonResponse(JSONArray(results.map(_.toJSON): _*))
  }


  //
  // API Methods
  //

  /** gets the version of the MMT System */
  private def getMMTVersion : Option[IMMTVersionInfo] = {
    val versionNumber = MMTSystem.version.split(" ").head

    val format = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
    val buildDate = MMTSystem.buildTime.map(format.parse).map(_.getTime.toString)

    Some(IMMTVersionInfo(versionNumber, buildDate))
  }

  /** helper method to build a MathHubAPI Context
    * TODO: Figure out global caching
    */
  private def context: MathHubAPIContext = new MathHubAPIContext(controller, this.report)

  private def getURI(uri: String) : Option[IReferencable] = {
    log(s"getObject($uri)")
    context.getObject(uri)
  }
  private def getGroups() : List[IGroupRef] = {
    log(s"getGroups()")
    context.getGroups()
  }
  private def getGroup(id: String) : Option[IGroup] = {
    log(s"getGroup($id)")
    context.getGroup(id)
  }
  private def getArchive(id: String) : Option[IArchive] = {
    log(s"getArchive($id)")
    context.getArchive(id)
  }
  private def getModule(id: String): Option[IModule] = {
    log(s"getModule($id)")
    context.getModule(id)
  }
  private def getDocument(id: String): Option[IDocument] = {
    log(s"getDocument($id)")
    context.getDocument(id)
  }
}
