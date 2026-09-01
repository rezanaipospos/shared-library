/**
 * notifySlack — kirim notifikasi Slack.
 * Aman dipanggil walau channel kosong (no-op).
 *
 * Menggunakan global Slack config dari Jenkins (Manage Jenkins → System → Slack).
 * Tidak perlu tokenCredentialId di sini — diambil dari global config otomatis.
 */
def call(String channel, String status, String message, def previousThread) {
    if (!channel?.trim()) {
        echo "[slack:skip] channel kosong, skip notifikasi."
        return null
    }

    def color = [
        'STARTED': '#439FE0',
        'SUCCESS': 'good',
        'FAILURE': 'danger'
    ].get(status, '#cccccc')

    def text = "*${status}* — ${message}\nJob: ${env.JOB_NAME} #${env.BUILD_NUMBER}\n${env.BUILD_URL}"

    try {
        // Tidak pakai tokenCredentialId — rely on global Jenkins Slack config
        // (sama dengan pattern di example-project/postslack.groovy yang bekerja)
        def resp = slackSend(
            channel: channel,
            color: color,
            message: text
        )
        return resp
    } catch (Exception e) {
        echo "[slack:error] Gagal kirim notifikasi: ${e.getMessage()}"
        return null
    }
}
