import { DocumentLoader } from '@/libs/document';
import { formatAuthors, formatPublisher, formatTitle } from '@/utils/book';

export const readCloudImportMetadata = async (file: File) => {
  const { book, format } = await new DocumentLoader(file).open();
  try {
    if (format !== 'EPUB') throw new Error('Cloud import requires an EPUB file');
    const { title, author, language, isbn, publisher } = book.metadata;
    return {
      title: formatTitle(title),
      author: formatAuthors(author, language),
      language: Array.isArray(language) ? language[0] : language,
      isbn,
      publisher: publisher ? formatPublisher(publisher) : undefined,
    };
  } finally {
    await book.destroy?.();
  }
};

export const extractCloudBookSections = async (file: File) => {
  const { book, format } = await new DocumentLoader(file).open();
  try {
    if (format !== 'EPUB') throw new Error('Cloud indexing requires an EPUB file');
    const sections: { index: number; title: string; text: string }[] = [];
    let totalCharacters = 0;
    for (const [offset, section] of book.sections.entries()) {
      const document = await section.createDocument();
      const body = document.querySelector('body');
      if (!body) continue;
      body.querySelectorAll('script,style').forEach((element) => element.remove());
      body.querySelectorAll('p,div,li,br,h1,h2,h3,h4,h5,h6,blockquote').forEach((element) => {
        element.append(document.createTextNode('\n'));
      });
      const text = body.textContent?.trim() ?? '';
      if (!text) continue;
      const index = offset + 1;
      const title =
        body.querySelector('h1,h2,h3')?.textContent?.trim() ||
        document.querySelector('title')?.textContent?.trim() ||
        `Chapter ${index}`;
      totalCharacters += text.length;
      if (index > 5000 || text.length > 500_000 || totalCharacters > 8_000_000) {
        throw new Error('Cloud book exceeds the indexing size limit');
      }
      sections.push({ index, title: title.slice(0, 255), text });
    }
    return sections;
  } finally {
    await book.destroy?.();
  }
};
