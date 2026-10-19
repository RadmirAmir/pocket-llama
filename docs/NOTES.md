# Notes / Заметки

## English

Open **Notes** in the bottom navigation, then **New note**. Add a title and text: Pocket saves automatically. **Saved** means the native encrypted store has acknowledged the latest edit. Back and Done also save; a failed write keeps the editor open with your text and a Retry saving action.

- Search titles and note text, including Russian and English. Search ignores case; Russian е/ё match. Punctuation is literal. Results include a text preview; older matches are available with Show more.
- Pin important notes with the pin button. Pinned notes appear first; other notes follow their last edit time.
- Use Checklist to insert a checkbox or toggle the checkbox on the current line. Enter continues the list; Enter on an empty item ends it. These are plain-text checkboxes, not rich document formatting.
- Move a note to Trash through its menu. Open Trash to search, read or restore deleted notes. They stay there until you explicitly confirm permanent deletion.
- Interface labels follow Settings → Interface language. Your own titles and text are never translated.

Notes work offline and need no language model. Titles, text and search fields are in a separate SQLCipher database, protected by the existing vault key and app PIN. The editor cannot connect to the network. There is no account, sync, automatic cloud backup or automatic trash expiry. Uninstalling Pocket or clearing app data removes access to notes, as it does to other vault content. Notes currently support plain text, up to 200 characters in a title and 200,000 in a body; attachments and folders are not part of this version.

## Русский

Откройте **«Заметки»** в нижней навигации и нажмите **«Новая заметка»**. Добавьте заголовок и текст: Pocket сохраняет их автоматически. Надпись **«Сохранено»** появляется после подтверждения последней правки защищённым хранилищем. Кнопки «Назад» и «Готово» тоже сохраняют текст; при ошибке редактор остаётся открыт и предлагает повторить сохранение.

- Поиск работает по заголовкам и тексту, включая русский и английский. Регистр не важен, е/ё совпадают. Знаки препинания ищутся буквально. В результатах есть фрагменты текста; старые результаты доступны через «Показать ещё».
- Закрепляйте важное кнопкой с булавкой. Закреплённые заметки идут первыми, остальные — по времени последней правки.
- Кнопка «Список» добавляет галочку или переключает её у текущей строки. Enter продолжает список; Enter в пустом пункте завершает его. Галочки хранятся как обычный текст.
- Через меню можно переместить заметку в корзину. Там её можно найти, прочитать и восстановить. Окончательное удаление требует отдельного подтверждения; автоматической очистки нет.
- Подписи следуют языку EN/RU из настроек. Ваши заголовки и текст не переводятся.

Заметки работают без интернета и языковой модели. Заголовки, текст и поля поиска находятся в отдельной базе SQLCipher под защитой существующего ключа хранилища и PIN приложения. Редактор не может обращаться в сеть. Облачной синхронизации и автоматического бэкапа нет. Удаление Pocket или очистка данных приложения удаляет доступ и к заметкам. В этой версии поддерживаются обычный текст, заголовки до 200 символов и текст до 200 000 символов; вложения и папки пока не реализованы.
