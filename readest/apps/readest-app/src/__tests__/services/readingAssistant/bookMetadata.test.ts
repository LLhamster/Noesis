import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  extractCloudBookSections,
  readCloudImportMetadata,
} from '@/services/readingAssistant/bookMetadata';

describe('readCloudImportMetadata', () => {
  it('reads embedded metadata independently of the file name', async () => {
    const fixture = resolve(__dirname, '../../fixtures/data/sample-alice.epub');
    const file = new File([readFileSync(fixture)], 'renamed.epub');
    const metadata = await readCloudImportMetadata(file);
    expect(metadata.title).toMatch(/alice/i);
    expect(metadata.author).toMatch(/carroll/i);
  });
  it('extracts searchable text in EPUB spine order', async () => {
    const fixture = resolve(__dirname, '../../fixtures/data/sample-alice.epub');
    const file = new File([readFileSync(fixture)], 'alice.epub');
    const sections = await extractCloudBookSections(file);
    expect(sections.length).toBeGreaterThan(1);
    expect(sections.map(({ index }) => index)).toEqual(
      sections.map(({ index }) => index).sort((a, b) => a - b),
    );
    expect(sections.some(({ text }) => /rabbit/i.test(text))).toBe(true);
  });
});
