/** Read historical message envelopes while emitting only the current protocol. */
export function normalizeSakuraProtocol(text: string): string {
  return text
    .replace(/(\[\[\/?)AID_SCRIPT_(SELECTION_V1|SELECTIONS_V2|COMMENT_V1|REPLACEMENTS_V1)(\]\])/g, '$1SAKURA_SCRIPT_$2$3')
    .replace(/\[AID_MODE:SCREENPLAY\]/g, '[SAKURA_MODE:SCREENPLAY]')
}
