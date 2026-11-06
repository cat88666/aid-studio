import type { CreatorLocale } from './creatorLanguages'

type InterfaceText = {
  description: string
  login: string
  reset: string
  projects: string
  loading: string
  desktop: string
  copy: string
  copied: string
  copyError: string
}

function labels(description: string, login: string, reset: string, projects: string, loading: string, desktop: string, copy: string, copied: string, copyError: string): InterfaceText {
  return { description, login, reset, projects, loading, desktop, copy, copied, copyError }
}

export const creatorInterfaceText: Record<CreatorLocale, InterfaceText> = {
  en: labels('Create short films with AI, from your first script to the final cut.', 'Sign in', 'Reset password', 'My projects', 'Loading…', 'Open the studio on a computer', 'Copy desktop link', 'Link copied.', 'Copy the link manually.'),
  zh: labels('用 AI 创作短片，从剧本和分镜到最终成片。', '登录', '找回密码', '我的作品', '加载中…', '请在电脑上打开工作台', '复制电脑端地址', '地址已复制。', '请手动复制地址。'),
  'zh-hant': labels('用 AI 創作短片，從劇本、分鏡到最終成片。', '登入', '重設密碼', '我的作品', '載入中…', '請在電腦上開啟工作室', '複製電腦版連結', '已複製連結。', '請手動複製連結。'),
  ja: labels('脚本と絵コンテから編集まで、AIで短編映画を制作。', 'ログイン', 'パスワードの再設定', 'マイプロジェクト', '読み込み中…', 'パソコンでスタジオを開いてください', 'パソコン用リンクをコピー', 'リンクをコピーしました。', 'リンクを手動でコピーしてください。'),
  ko: labels('대본부터 최종 편집까지 AI로 단편 영화를 만드세요.', '로그인', '비밀번호 재설정', '내 프로젝트', '불러오는 중…', '컴퓨터에서 스튜디오를 여세요', '데스크톱 링크 복사', '링크를 복사했습니다.', '링크를 직접 복사하세요.'),
  pt: labels('Crie curtas com IA, do roteiro à edição final.', 'Entrar', 'Redefinir senha', 'Meus projetos', 'Carregando…', 'Abra o estúdio no computador', 'Copiar link', 'Link copiado.', 'Copie o link manualmente.'),
  es: labels('Crea cortometrajes con IA, desde el guion hasta el montaje final.', 'Iniciar sesión', 'Restablecer contraseña', 'Mis proyectos', 'Cargando…', 'Abre el estudio en un ordenador', 'Copiar enlace', 'Enlace copiado.', 'Copia el enlace manualmente.'),
  id: labels('Buat film pendek dengan AI, dari naskah hingga penyuntingan akhir.', 'Masuk', 'Atur ulang kata sandi', 'Proyek saya', 'Memuat…', 'Buka studio di komputer', 'Salin tautan', 'Tautan disalin.', 'Salin tautan secara manual.'),
  ru: labels('Создавайте короткометражные фильмы с ИИ — от сценария до монтажа.', 'Вход', 'Сброс пароля', 'Мои проекты', 'Загрузка…', 'Откройте студию на компьютере', 'Скопировать ссылку', 'Ссылка скопирована.', 'Скопируйте ссылку вручную.'),
  fr: labels('Créez des courts métrages avec l’IA, du scénario au montage final.', 'Connexion', 'Réinitialiser le mot de passe', 'Mes projets', 'Chargement…', 'Ouvrez le studio sur ordinateur', 'Copier le lien', 'Lien copié.', 'Copiez le lien manuellement.'),
  it: labels('Crea cortometraggi con l’IA, dalla sceneggiatura al montaggio finale.', 'Accedi', 'Reimposta password', 'I miei progetti', 'Caricamento…', 'Apri lo studio sul computer', 'Copia link', 'Link copiato.', 'Copia il link manualmente.'),
  nl: labels('Maak korte films met AI, van het eerste script tot de eindmontage.', 'Inloggen', 'Wachtwoord herstellen', 'Mijn projecten', 'Laden…', 'Open de studio op een computer', 'Link kopiëren', 'Link gekopieerd.', 'Kopieer de link handmatig.'),
  ar: labels('أنشئ أفلامًا قصيرة بالذكاء الاصطناعي، من السيناريو إلى المونتاج النهائي.', 'تسجيل الدخول', 'إعادة تعيين كلمة المرور', 'مشاريعي', 'جارٍ التحميل…', 'افتح الاستوديو على جهاز الكمبيوتر', 'نسخ الرابط', 'تم نسخ الرابط.', 'انسخ الرابط يدويًا.'),
  tr: labels('Senaryodan son kurguya kadar yapay zekâ ile kısa filmler oluşturun.', 'Giriş yap', 'Şifreyi sıfırla', 'Projelerim', 'Yükleniyor…', 'Stüdyoyu bilgisayarda açın', 'Bağlantıyı kopyala', 'Bağlantı kopyalandı.', 'Bağlantıyı elle kopyalayın.'),
  de: labels('Erstelle Kurzfilme mit KI, vom Drehbuch bis zum fertigen Schnitt.', 'Anmelden', 'Passwort zurücksetzen', 'Meine Projekte', 'Wird geladen…', 'Öffne das Studio auf einem Computer', 'Link kopieren', 'Link kopiert.', 'Kopiere den Link manuell.'),
  uk: labels('Створюйте короткометражні фільми за допомогою ШІ — від сценарію до монтажу.', 'Увійти', 'Скинути пароль', 'Мої проєкти', 'Завантаження…', 'Відкрийте студію на комп’ютері', 'Скопіювати посилання', 'Посилання скопійовано.', 'Скопіюйте посилання вручну.'),
}
