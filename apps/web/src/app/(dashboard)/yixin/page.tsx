"use client";

import { Indie_Flower } from "next/font/google";
import { SidebarTrigger } from "@/components/ui/sidebar";

const indieFlower = Indie_Flower({
  subsets: ["latin"],
  weight: "400",
});

const poem = [
  [
    "dear yixin, where do i even start?",
    "it's been a whole year since we've been apart.",
    "seeing your smile would brighten my day,",
    "now that i've left, things aren't the same way.",
  ],
  [
    "some people win... and then there's you.",
    "handsome, smart, and rich, to name a few.",
    "the universe looked around and chose him,",
    'life really said "let\'s give everything to yixin".',
  ],
  [
    "every day is so dull, as you can see,",
    "i wish i was gundam so you could play with me.",
    "the reels you send me break my heart",
    "cuz they remind of a perfect thing torn apart.",
  ],
  [
    "if you found this letter deep in the code,",
    "you discovered my emotional payload.",
    "at this point, i want to make it clear,",
    "this is the closest i've felt to you all year.",
  ],
];

const lineStyle = "text-xs md:text-lg text-[#2c2c2c] leading-7 md:leading-8";

export default function YixinPage() {
  return (
    <div className="flex flex-col min-h-screen">
      <div className="flex items-center gap-2 p-4 pb-0">
        <SidebarTrigger className="md:hidden" />
      </div>

      <div className="flex flex-1 items-center justify-center px-4 pb-6">
        <div className="max-w-lg w-full bg-white rounded-lg shadow-xl">
          <div
            className={`${indieFlower.className} px-6 py-8 md:px-10 md:py-10`}
          >
            {poem.map((stanza, stanzaIndex) => (
              <div
                key={stanzaIndex}
                className={stanzaIndex > 0 ? "mt-5 md:mt-6" : ""}
              >
                {stanza.map((line, lineIndex) => (
                  <p key={lineIndex} className={lineStyle}>
                    {line}
                  </p>
                ))}
              </div>
            ))}

            <p className={`${lineStyle} mt-8 md:mt-10 text-right`}>- felipe</p>
          </div>
        </div>
      </div>
    </div>
  );
}
