import type { CreatorLocale } from '~/config/creatorLanguages'

export type HelpLabels = readonly [search: string, all: string, loading: string, empty: string, failed: string, retry: string, noAnswer: string]

export const faqLabels: Record<CreatorLocale, HelpLabels> = {
  ja: ['質問を検索', 'すべて', '読み込み中…', '該当する質問はありません。', '読み込みに失敗しました。', '再試行', '回答はまだありません。'],
  en: ['Search questions', 'All', 'Loading…', 'No matching questions.', 'Couldn’t load this content.', 'Try again', 'No answer yet.'],
  zh: ['搜索问题', '全部', '加载中…', '暂无相关问题。', '内容加载失败。', '重试', '暂无回答。'],
  'zh-hant': ['搜尋問題', '全部', '載入中…', '沒有符合的問題。', '內容載入失敗。', '重試', '暫無回答。'],
  ko: ['질문 검색', '전체', '불러오는 중…', '일치하는 질문이 없습니다.', '내용을 불러올 수 없습니다.', '다시 시도', '아직 답변이 없습니다.'],
  pt: ['Pesquisar perguntas', 'Todas', 'Carregando…', 'Nenhuma pergunta encontrada.', 'Não foi possível carregar o conteúdo.', 'Tentar novamente', 'Ainda sem resposta.'],
  es: ['Buscar preguntas', 'Todas', 'Cargando…', 'No hay preguntas coincidentes.', 'No se pudo cargar el contenido.', 'Reintentar', 'Todavía no hay respuesta.'],
  id: ['Cari pertanyaan', 'Semua', 'Memuat…', 'Tidak ada pertanyaan yang cocok.', 'Konten gagal dimuat.', 'Coba lagi', 'Belum ada jawaban.'],
  ru: ['Поиск вопросов', 'Все', 'Загрузка…', 'Подходящих вопросов нет.', 'Не удалось загрузить материалы.', 'Повторить', 'Ответа пока нет.'],
  fr: ['Rechercher une question', 'Toutes', 'Chargement…', 'Aucune question correspondante.', 'Impossible de charger le contenu.', 'Réessayer', 'Pas encore de réponse.'],
  it: ['Cerca domande', 'Tutte', 'Caricamento…', 'Nessuna domanda trovata.', 'Impossibile caricare il contenuto.', 'Riprova', 'Ancora nessuna risposta.'],
  nl: ['Zoek vragen', 'Alle', 'Laden…', 'Geen vragen gevonden.', 'De inhoud kon niet worden geladen.', 'Opnieuw proberen', 'Nog geen antwoord.'],
  ar: ['ابحث عن سؤال', 'الكل', 'جارٍ التحميل…', 'لا توجد أسئلة مطابقة.', 'تعذّر تحميل المحتوى.', 'إعادة المحاولة', 'لا توجد إجابة بعد.'],
  tr: ['Soru ara', 'Tümü', 'Yükleniyor…', 'Eşleşen soru yok.', 'İçerik yüklenemedi.', 'Yeniden dene', 'Henüz yanıt yok.'],
  de: ['Fragen durchsuchen', 'Alle', 'Wird geladen…', 'Keine passenden Fragen.', 'Der Inhalt konnte nicht geladen werden.', 'Erneut versuchen', 'Noch keine Antwort.'],
  uk: ['Пошук запитань', 'Усі', 'Завантаження…', 'Запитань не знайдено.', 'Не вдалося завантажити вміст.', 'Спробувати ще', 'Відповіді ще немає.']
}
