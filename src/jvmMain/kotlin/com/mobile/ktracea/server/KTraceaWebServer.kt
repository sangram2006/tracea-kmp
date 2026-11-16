package com.mobile.ktracea.server

import com.mobile.ktracea.KTracea
import com.mobile.ktracea.export.HarExporter
import com.mobile.ktracea.mock.MockEngine
import com.mobile.ktracea.model.MockRule
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

class KTraceaWebServer(private val port: Int = 8080) {

    private var engine: ApplicationEngine? = null

    fun start() {
        if (engine != null) return

        engine = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            routing {
                get("/") {
                    call.respondText(getDashboardHtml(), ContentType.Text.Html)
                }

                get("/api/transactions") {
                    val list = KTracea.repository.transactions.value
                    call.respond(list)
                }

                get("/api/transactions/{id}") {
                    val id = call.parameters["id"]
                    val tx = id?.let { KTracea.repository.getById(it) }
                    if (tx != null) {
                        call.respond(tx)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Transaction not found"))
                    }
                }

                get("/api/har") {
                    val list = KTracea.repository.transactions.value
                    val harJson = HarExporter.exportToHar(list)
                    call.respondText(harJson, ContentType.Application.Json)
                }

                get("/api/mocks") {
                    call.respond(MockEngine.rules.value)
                }

                post("/api/mocks") {
                    val rule = call.receive<MockRule>()
                    MockEngine.addRule(rule)
                    call.respond(HttpStatusCode.Created, rule)
                }

                delete("/api/mocks/{id}") {
                    val id = call.parameters["id"]
                    if (id != null) {
                        MockEngine.removeRule(id)
                        call.respond(HttpStatusCode.OK, mapOf("deleted" to id))
                    } else {
                        call.respond(HttpStatusCode.BadRequest)
                    }
                }

                post("/api/clear") {
                    KTracea.clearAll()
                    call.respond(HttpStatusCode.OK, mapOf("status" to "cleared"))
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
    }

    private fun getDashboardHtml(): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>KTracea Web Dashboard</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 20px; }
                header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 20px; border-bottom: 1px solid #334155; }
                h1 { font-size: 24px; color: #38bdf8; }
                .subtitle { font-size: 13px; color: #94a3b8; }
                .btn { padding: 8px 16px; border-radius: 6px; border: none; cursor: pointer; font-weight: 600; font-size: 13px; text-decoration: none; }
                .btn-primary { background: #38bdf8; color: #0f172a; }
                .btn-danger { background: #f87171; color: #fff; }
                .table-container { margin-top: 20px; overflow-x: auto; background: #1e293b; border-radius: 8px; border: 1px solid #334155; }
                table { width: 100%; border-collapse: collapse; font-size: 13px; }
                th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid #334155; }
                th { background: #0f172a; color: #94a3b8; font-weight: 600; }
                tr:hover { background: #334155; cursor: pointer; }
                .badge { padding: 3px 8px; border-radius: 4px; font-weight: bold; font-size: 11px; }
                .badge-get { background: #0284c7; color: white; }
                .badge-post { background: #16a34a; color: white; }
                .badge-put { background: #d97706; color: white; }
                .badge-delete { background: #dc2626; color: white; }
                .status-2xx { color: #4ade80; font-weight: bold; }
                .status-4xx { color: #fbbf24; font-weight: bold; }
                .status-5xx { color: #f87171; font-weight: bold; }
            </style>
        </head>
        <body>
            <header>
                <div>
                    <h1>📡 KTracea Network Dashboard</h1>
                    <p class="subtitle">Kotlin Multiplatform Network Inspector by Sangram Mohanty</p>
                </div>
                <div style="display: flex; gap: 10px;">
                    <a href="/api/har" class="btn btn-primary" download="tracea-network.har">Export HAR</a>
                    <button onclick="clearLogs()" class="btn btn-danger">Clear Logs</button>
                </div>
            </header>
            <div class="table-container">
                <table>
                    <thead>
                        <tr>
                            <th>Method</th>
                            <th>Status</th>
                            <th>URL</th>
                            <th>Duration</th>
                            <th>Time</th>
                        </tr>
                    </thead>
                    <tbody id="transactions-body">
                        <tr><td colspan="5" style="text-align: center; color: #64748b;">Loading network traffic...</td></tr>
                    </tbody>
                </table>
            </div>
            <script>
                async function fetchLogs() {
                    try {
                        const res = await fetch('/api/transactions');
                        const data = await res.json();
                        const tbody = document.getElementById('transactions-body');
                        if (data.length === 0) {
                            tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: #64748b;">No network requests recorded yet</td></tr>';
                            return;
                        }
                        tbody.innerHTML = data.map(tx => {
                            const methodClass = 'badge-' + (tx.method || 'get').toLowerCase();
                            const status = tx.responseCode || 'ERR';
                            const statusClass = status >= 200 && status < 300 ? 'status-2xx' : (status >= 400 && status < 500 ? 'status-4xx' : 'status-5xx');
                            return `<tr>
                                <td><span class="badge ${'$'}{methodClass}">${'$'}{tx.method}</span></td>
                                <td class="${'$'}{statusClass}">${'$'}{status}</td>
                                <td style="word-break: break-all;">${'$'}{tx.url}</td>
                                <td>${'$'}{tx.durationMs || 0} ms</td>
                                <td>${'$'}{new Date(tx.timestamp).toLocaleTimeString()}</td>
                            </tr>`;
                        }).join('');
                    } catch (e) {
                        console.error(e);
                    }
                }
                async function clearLogs() {
                    await fetch('/api/clear', { method: 'POST' });
                    fetchLogs();
                }
                fetchLogs();
                setInterval(fetchLogs, 2000);
            </script>
        </body>
        </html>
    """.trimIndent()
}
