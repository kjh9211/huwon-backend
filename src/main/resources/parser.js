/**
 * 후원 알림의 제목과 본문을 파싱해서 마인크래프트 후원 처리에 필요한 값을 반환합니다.
 *
 * @param {string} title 알림 제목
 * @param {string} content 알림 본문
 * @param {string} appname 알림을 보낸 앱 이름
 * @param {string} time 알림 수신 시각 문자열
 * @returns {{playerName: string, donorName: string | null, amount: number | null}}
 */
function parseDonation(title, content, appname, time) {
  const text = `${title}\n${content}`;

  const playerMatch =
    text.match(/player\s*[:=]\s*([A-Za-z0-9_]{3,16})/i) ||
    text.match(/nickname\s*[:=]\s*([A-Za-z0-9_]{3,16})/i) ||
    text.match(/닉네임\s*[:=]\s*([A-Za-z0-9_]{3,16})/i) ||
    text.match(/후원대상\s*[:=]\s*([A-Za-z0-9_]{3,16})/i);

  const donorMatch =
    text.match(/donor\s*[:=]\s*([^\n]+)/i) ||
    text.match(/후원자\s*[:=]\s*([^\n]+)/i);

  const amountMatch =
    text.match(/amount\s*[:=]\s*([0-9,]+)/i) ||
    text.match(/금액\s*[:=]\s*([0-9,]+)/i) ||
    text.match(/([0-9,]+)원/i);

  return {
    playerName: playerMatch ? playerMatch[1].trim() : "",
    donorName: donorMatch ? donorMatch[1].trim() : null,
    amount: amountMatch ? Number(amountMatch[1].replace(/,/g, "")) : null
  };
}
