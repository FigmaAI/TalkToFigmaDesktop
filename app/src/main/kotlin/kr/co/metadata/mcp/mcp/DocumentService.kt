package kr.co.metadata.mcp.mcp

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking

/**
 * Document and Selection service for Figma MCP tools
 * Handles document information and selection-related operations
 */
object DocumentService : BaseFigmaService() {
    
    /**
     * Register all document-related tools
     */
    fun registerTools(server: Server, figmaCommandSender: suspend (String, Map<String, Any>) -> Any) {
        logger.info { "Registering document service tools..." }
        
        registerGetDocumentInfo(server, figmaCommandSender)
        registerGetSelection(server, figmaCommandSender)
        registerReadMyDesign(server, figmaCommandSender)
        registerGetNodeInfo(server, figmaCommandSender)
        registerGetNodesInfo(server, figmaCommandSender)
        
        logger.info { "Document service tools registered successfully" }
    }
    
    /**
     * Get detailed information about the current Figma document
     */
    private fun registerGetDocumentInfo(server: Server, figmaCommandSender: suspend (String, Map<String, Any>) -> Any) {
        server.addTool(
            name = "get_document_info",
            description = "Get detailed information about the current Figma document",
            inputSchema = Tool.Input()
        ) { _ ->
            kotlinx.coroutines.runBlocking {
                executeFigmaCommand(
                    command = "get_document_info",
                    params = emptyMap(),
                    figmaCommandSender = figmaCommandSender,
                    operation = "getting document info"
                )
            }
        }
    }
    
    /**
     * Get information about the current selection in Figma
     */
    private fun registerGetSelection(server: Server, figmaCommandSender: suspend (String, Map<String, Any>) -> Any) {
        server.addTool(
            name = "get_selection",
            description = "Get information about the current selection in Figma",
            inputSchema = Tool.Input()
        ) { _ ->
            kotlinx.coroutines.runBlocking {
                executeFigmaCommand(
                    command = "get_selection",
                    params = emptyMap(),
                    figmaCommandSender = figmaCommandSender,
                    operation = "getting selection"
                )
            }
        }
    }
    
    /**
     * Get detailed information about the current selection in Figma, including all node details
     */
    private fun registerReadMyDesign(server: Server, figmaCommandSender: suspend (String, Map<String, Any>) -> Any) {
        server.addTool(
            name = "read_my_design",
            description = "Get detailed information about the current selection in Figma, including all node details",
            inputSchema = Tool.Input()
        ) { _ ->
            kotlinx.coroutines.runBlocking {
                executeFigmaCommand(
                    command = "read_my_design",
                    params = emptyMap(),
                    figmaCommandSender = figmaCommandSender,
                    operation = "reading design"
                )
            }
        }
    }

    /**
     * Get detailed information about a specific node in Figma
     */
    private fun registerGetNodeInfo(server: Server, figmaCommandSender: suspend (String, Map<String, Any>) -> Any) {
        server.addTool(
            name = "get_node_info",
            description = "Get detailed information about a specific node in Figma",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("nodeId") {
                        put("type", "string")
                        put("description", "The ID of the node to get information about")
                    }
                },
                required = listOf("nodeId")
            )
        ) { request ->
            kotlinx.coroutines.runBlocking {
                try {
                    val nodeId = request.arguments["nodeId"].safeString("nodeId")
                    
                    val result = figmaCommandSender("get_node_info", mapOf("nodeId" to nodeId))
                    createSuccessResponse("Retrieved detailed information for node: $result")
                } catch (e: Exception) {
                    createErrorResponse("getting node info", e)
                }
            }
        }
    }

    /**
     * Get detailed information about multiple nodes in Figma
     */
    private fun registerGetNodesInfo(server: Server, figmaCommandSender: suspend (String, Map<String, Any>) -> Any) {
        server.addTool(
            name = "get_nodes_info", 
            description = "Get detailed information about multiple nodes in Figma",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("nodeIds") {
                        put("type", "array")
                        put("description", "Array of node IDs to get information about")
                        putJsonObject("items") {
                            put("type", "string")
                        }
                    }
                },
                required = listOf("nodeIds")
            )
        ) { request ->
            runBlocking {
                try {
                    val nodeIds = request.arguments["nodeIds"]?.jsonArray
                        ?: throw IllegalArgumentException("nodeIds array is required")
                    
                    val params = mapOf("nodeIds" to nodeIds.map { it.jsonPrimitive.content })
                    val result = figmaCommandSender("get_nodes_info", params)
                    createSuccessResponse("Retrieved information for ${nodeIds.size} nodes: $result")
                } catch (e: Exception) {
                    createErrorResponse("getting nodes info", e)
                }
            }
        }
    }
} 